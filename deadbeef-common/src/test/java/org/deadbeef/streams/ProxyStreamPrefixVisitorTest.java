package org.deadbeef.streams;

import com.google.common.base.Strings;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThreadLocalRandom;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.deadbeef.protocol.HttpProto;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ProxyStreamPrefixVisitorTest {

  @Rule public RunTestOnContext rule = new RunTestOnContext();

  // ---------- constructor validation ----------

  private static HttpProto.Request sampleRequest() {
    return HttpProto.Request.newBuilder()
        .setHeaders(
            HttpProto.Headers.newBuilder().setAccept("*/*").setContentType("application/json"))
        .setMethod(HttpProto.Method.POST)
        .setScheme("https")
        .setVersion(HttpProto.Version.HTTP_1_1)
        .build();
  }

  private static HttpProto.Request parseRequest(TestContext ctx, Buffer prefixBuffer) {
    try {
      return HttpProto.Request.parseFrom(prefixBuffer.getBytes());
    } catch (InvalidProtocolBufferException e) {
      ctx.fail(e);
      throw new AssertionError(e);
    }
  }

  private static Buffer randomBytes(int len) {
    byte[] bytes = PlatformDependent.allocateUninitializedArray(len);
    ThreadLocalRandom.current().nextBytes(bytes);
    return Buffer.buffer(bytes);
  }

  // ---------- visit() param validation ----------

  private static Buffer rawHeader(int magic, int bodyLen) {
    ByteBuf buf = Unpooled.buffer(8);
    buf.writeInt(magic);
    buf.writeInt(bodyLen);
    return Buffer.buffer(buf);
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullVertxInTwoArgConstructor() {
    new ProxyStreamPrefixVisitor<WriteStream<Buffer>>(null, new DefaultPipeFactory());
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullPipeFactory() {
    new ProxyStreamPrefixVisitor<WriteStream<Buffer>>(rule.vertx(), null);
  }

  // ---------- happy paths ----------

  @Test
  public void singleArgConstructorUsesDefaultPipeFactory() {
    // Just builds; no NPE expected.
    new ProxyStreamPrefixVisitor<WriteStream<Buffer>>(rule.vertx());
  }

  @Test(expected = NullPointerException.class)
  public void visitRejectsNullSrcInFutureForm() {
    new ProxyStreamPrefixVisitor<WriteStream<Buffer>>(rule.vertx()).visit(null);
  }

  @Test(expected = NullPointerException.class)
  public void visitRejectsNullSrcInHandlerForm() {
    new ProxyStreamPrefixVisitor<WriteStream<Buffer>>(rule.vertx()).visit(null, ar -> {});
  }

  @Test(expected = NullPointerException.class)
  public void visitRejectsNullHandler() {
    new ProxyStreamPrefixVisitor<WriteStream<Buffer>>(rule.vertx())
        .visit(new FakeReadStream(), null);
  }

  @Test
  public void parsesPrefixDeliveredInOneChunk(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          HttpProto.Request expected = sampleRequest();
          Buffer prefix = Prefix.serializeToBuffer(expected);
          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx, new DefaultPipeFactory())
              .visit(src)
              .onFailure(ctx::fail)
              .onSuccess(
                  pa -> {
                    HttpProto.Request parsed = parseRequest(ctx, pa.get());
                    ctx.assertEquals(expected, parsed);
                    done.complete();
                  });
          // The visitor pauses src, then sets the data handler; emit drives it.
          src.emit(prefix);
        });
  }

  @Test
  public void parsesPrefixSplitByteByByte(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          HttpProto.Request expected = sampleRequest();
          Buffer prefix = Prefix.serializeToBuffer(expected);
          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx, new DefaultPipeFactory())
              .visit(src)
              .onFailure(ctx::fail)
              .onSuccess(
                  pa -> {
                    ctx.assertEquals(expected, parseRequest(ctx, pa.get()));
                    done.complete();
                  });
          for (int i = 0; i < prefix.length(); i++) {
            src.emit(prefix.slice(i, i + 1));
          }
        });
  }

  // ---------- failure paths ----------

  @Test
  public void copyWritesTailWhenStreamAlreadyEnded(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          CollectingWriteStream dst = new CollectingWriteStream();
          HttpProto.Request msg = sampleRequest();
          Buffer prefix = Prefix.serializeToBuffer(msg);
          byte[] tail = "trailing-body".getBytes();

          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx, new DefaultPipeFactory())
              .visit(src)
              .onFailure(ctx::fail)
              .onSuccess(
                  pa ->
                      pa.apply(dst)
                          .onFailure(ctx::fail)
                          .onSuccess(
                              ignored -> {
                                ctx.assertEquals(1, dst.received.size());
                                ctx.assertTrue(Arrays.equals(tail, dst.received.get(0).getBytes()));
                                done.complete();
                              }));

          // Emit prefix + tail in one chunk, then end the stream BEFORE copy runs.
          Buffer combined = Buffer.buffer().appendBuffer(prefix).appendBytes(tail);
          src.emit(combined);
          src.end();
        });
  }

  @Test
  public void copyShortCircuitsWhenNoRemainingAndStreamEnded(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          CollectingWriteStream dst = new CollectingWriteStream();
          Buffer prefix = Prefix.serializeToBuffer(sampleRequest());

          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx, new DefaultPipeFactory())
              .visit(src)
              .onFailure(ctx::fail)
              .onSuccess(
                  pa ->
                      pa.apply(dst)
                          .onFailure(ctx::fail)
                          .onSuccess(
                              ignored -> {
                                ctx.assertTrue(dst.received.isEmpty());
                                done.complete();
                              }));
          src.emit(prefix);
          src.end();
        });
  }

  @Test
  public void copyPipesRestOfStreamWhenStillOpen(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          CollectingWriteStream dst = new CollectingWriteStream();
          Buffer prefix = Prefix.serializeToBuffer(sampleRequest());

          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx, new DefaultPipeFactory())
              .visit(src)
              .onFailure(ctx::fail)
              .onSuccess(
                  pa ->
                      pa.apply(dst)
                          .onFailure(ctx::fail)
                          .onSuccess(
                              ignored -> {
                                ctx.assertEquals(
                                    "alpha-beta",
                                    Buffer.buffer()
                                        .appendBuffer(dst.received.get(0))
                                        .appendBuffer(dst.received.get(1))
                                        .toString());
                                done.complete();
                              }));

          src.emit(prefix); // prefix only, no tail; stream stays open
          // The visitor sets up a pipe on the source; emit two more chunks then end.
          vertx.runOnContext(
              w -> {
                src.emit(Buffer.buffer("alpha-"));
                src.emit(Buffer.buffer("beta"));
                src.end();
              });
        });
  }

  @Test
  public void copyWritesTailAndContinuesPipingMoreBytes(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          CollectingWriteStream dst = new CollectingWriteStream();
          Buffer prefix = Prefix.serializeToBuffer(sampleRequest());
          byte[] tail = "TAIL".getBytes();

          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx, new DefaultPipeFactory())
              .visit(src)
              .onFailure(ctx::fail)
              .onSuccess(
                  pa ->
                      pa.apply(dst)
                          .onFailure(ctx::fail)
                          .onSuccess(
                              ignored -> {
                                Buffer all = Buffer.buffer();
                                for (Buffer b : dst.received) all.appendBuffer(b);
                                ctx.assertEquals("TAIL-more-data", all.toString());
                                done.complete();
                              }));

          // Prefix + tail in one chunk
          src.emit(Buffer.buffer().appendBuffer(prefix).appendBytes(tail));
          // Then additional pipe bytes while the stream stays open
          vertx.runOnContext(
              w -> {
                src.emit(Buffer.buffer("-more-data"));
                src.end();
              });
        });
  }

  @Test
  public void failsWhenMagicDoesNotMatch(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx)
              .visit(src)
              .onSuccess(pa -> ctx.fail("should have failed"))
              .onFailure(
                  cause -> {
                    ctx.assertTrue(
                        cause.getMessage().contains("Magic not match"),
                        "actual: " + cause.getMessage());
                    done.complete();
                  });
          src.emit(rawHeader(0xCAFEBABE, 10));
        });
  }

  @Test
  public void failsOnZeroBodyLength(TestContext ctx) {
    runFailingLengthTest(ctx, 0);
  }

  @Test
  public void failsOnNegativeBodyLength(TestContext ctx) {
    runFailingLengthTest(ctx, -1);
  }

  @Test
  public void failsOnBodyLengthOverEightMb(TestContext ctx) {
    // limit is 1 << 23 (8 MiB) inclusive; anything strictly greater fails.
    runFailingLengthTest(ctx, (1 << 23) + 1);
  }

  private void runFailingLengthTest(TestContext ctx, int badLen) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx)
              .visit(src)
              .onSuccess(pa -> ctx.fail("should have failed"))
              .onFailure(
                  cause -> {
                    ctx.assertTrue(
                        cause.getMessage().contains("Illegal prefix len"),
                        "actual: " + cause.getMessage());
                    done.complete();
                  });
          src.emit(rawHeader(Prefix.MAGIC, badLen));
        });
  }

  // ---------- existing real-HTTP integration test, kept as backstop ----------

  @Test
  public void streamEndingBeforePrefixFailsPromise(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx)
              .visit(src)
              .onSuccess(pa -> ctx.fail("should have failed"))
              .onFailure(
                  cause -> {
                    ctx.assertTrue(
                        cause.getMessage().contains("Stream ended"),
                        "actual: " + cause.getMessage());
                    done.complete();
                  });
          src.end();
        });
  }

  @Test
  public void customBodyLengthLimitIsEnforced(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          // Limit set to 10 bytes; sending a header that asks for 11.
          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx, new DefaultPipeFactory(), 10)
              .visit(src)
              .onSuccess(pa -> ctx.fail("should have failed"))
              .onFailure(
                  cause -> {
                    ctx.assertTrue(
                        cause.getMessage().contains("Illegal prefix len"),
                        "actual: " + cause.getMessage());
                    done.complete();
                  });
          src.emit(rawHeader(Prefix.MAGIC, 11));
        });
  }

  // ---------- helpers ----------

  @Test(expected = IllegalArgumentException.class)
  public void rejectsZeroBodyLengthLimit() {
    new ProxyStreamPrefixVisitor<WriteStream<Buffer>>(rule.vertx(), new DefaultPipeFactory(), 0);
  }

  @Test
  public void srcExceptionFailsTheFuture(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    vertx.runOnContext(
        v -> {
          FakeReadStream src = new FakeReadStream();
          new ProxyStreamPrefixVisitor<CollectingWriteStream>(vertx)
              .visit(src)
              .onSuccess(pa -> ctx.fail("should have failed"))
              .onFailure(
                  cause -> {
                    ctx.assertEquals("upstream-boom", cause.getMessage());
                    done.complete();
                  });
          src.fail(new RuntimeException("upstream-boom"));
        });
  }

  @Test
  public void httpRoundTripStillWorks(TestContext testContext) {
    int port = 15367;
    Vertx vertx = rule.vertx();
    Async async = testContext.async();
    HttpServer httpServer = vertx.createHttpServer();
    ProxyStreamPrefixVisitor<HttpServerResponse> resolver = new ProxyStreamPrefixVisitor<>(vertx);
    httpServer.requestHandler(
        request ->
            resolver
                .visit(request)
                .onFailure(testContext::fail)
                .onSuccess(
                    pa -> {
                      try {
                        HttpProto.Request.parseFrom(pa.get().getBytes());
                      } catch (InvalidProtocolBufferException e) {
                        testContext.fail(e);
                      }
                      HttpServerResponse response = request.response();
                      String len = request.getHeader(HttpHeaderNames.CONTENT_LENGTH);
                      response.putHeader(
                          HttpHeaderNames.CONTENT_LENGTH,
                          Integer.toString(Integer.parseInt(len) - pa.get().length() - 8));
                      pa.accept(response, ar -> response.end());
                    }));
    HttpClient httpClient = vertx.createHttpClient();
    httpServer
        .listen(port)
        .onComplete(
            ar -> {
              if (ar.failed()) {
                testContext.fail(ar.cause());
                return;
              }
              sendReq(vertx, httpClient, testContext, async, port);
            });
  }

  private void sendReq(
      Vertx vertx, HttpClient httpClient, TestContext testContext, Async async, int port) {
    httpClient
        .request(HttpMethod.POST, port, "127.0.0.1", "")
        .onFailure(testContext::fail)
        .onSuccess(
            request -> {
              Buffer prefix = Prefix.serializeToBuffer(sampleRequest());
              Buffer randomData = randomBytes(1 << 12);
              request.putHeader(
                  HttpHeaderNames.CONTENT_LENGTH,
                  Integer.toString(prefix.length() + randomData.length()));
              request.write(prefix);
              vertx.setTimer(
                  30,
                  id -> {
                    request.write(randomData.getBuffer(0, randomData.length() >> 1));
                    vertx.setTimer(
                        50,
                        id1 ->
                            request.end(
                                randomData.getBuffer(
                                    randomData.length() >> 1, randomData.length())));
                  });
              request
                  .response()
                  .onFailure(testContext::fail)
                  .onSuccess(
                      response ->
                          response
                              .body()
                              .onFailure(testContext::fail)
                              .onSuccess(
                                  buffer -> {
                                    testContext.assertTrue(
                                        Arrays.equals(randomData.getBytes(), buffer.getBytes()),
                                        Strings.lenientFormat(
                                            "%s vs %s", randomData.length(), buffer.length()));
                                    async.countDown();
                                  }));
            });
  }

  private static final class FakeReadStream implements ReadStream<Buffer> {
    Handler<Buffer> handler;
    Handler<Void> endHandler;
    Handler<Throwable> exceptionHandler;

    @Override
    public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
      this.exceptionHandler = handler;
      return this;
    }

    @Override
    public ReadStream<Buffer> handler(Handler<Buffer> handler) {
      this.handler = handler;
      return this;
    }

    @Override
    public ReadStream<Buffer> pause() {
      return this;
    }

    @Override
    public ReadStream<Buffer> resume() {
      return this;
    }

    @Override
    public ReadStream<Buffer> fetch(long amount) {
      return this;
    }

    @Override
    public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
      this.endHandler = endHandler;
      return this;
    }

    void emit(Buffer buffer) {
      if (handler != null) handler.handle(buffer);
    }

    void end() {
      if (endHandler != null) endHandler.handle(null);
    }

    void fail(Throwable cause) {
      if (exceptionHandler != null) exceptionHandler.handle(cause);
    }
  }

  private static final class CollectingWriteStream implements WriteStream<Buffer> {
    final List<Buffer> received = new ArrayList<>();
    boolean ended;

    @Override
    public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
      return this;
    }

    @Override
    public Future<Void> write(Buffer data) {
      received.add(data);
      return Future.succeededFuture();
    }

    @Override
    public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
      received.add(data);
      handler.handle(Future.succeededFuture());
    }

    @Override
    public Future<Void> end() {
      ended = true;
      return Future.succeededFuture();
    }

    @Override
    public void end(Handler<AsyncResult<Void>> handler) {
      ended = true;
      handler.handle(Future.succeededFuture());
    }

    @Override
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
      return this;
    }

    @Override
    public boolean writeQueueFull() {
      return false;
    }

    @Override
    public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
      return this;
    }
  }
}
