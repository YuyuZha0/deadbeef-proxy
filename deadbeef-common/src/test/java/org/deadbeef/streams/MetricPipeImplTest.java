package org.deadbeef.streams;

import com.codahale.metrics.MetricRegistry;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(VertxUnitRunner.class)
public class MetricPipeImplTest {

  @Rule public RunTestOnContext rule = new RunTestOnContext();

  @Test
  public void meterCountsBytesAndDstReceivesData(TestContext testContext) {
    Vertx vertx = rule.vertx();
    Async async = testContext.async();
    vertx.runOnContext(
        v -> {
          MetricRegistry registry = new MetricRegistry();
          MetricPipeFactory factory =
              new MetricPipeFactory(registry, StreamType.HTTP_DOWN, false, false);

          FakeReadStream src = new FakeReadStream();
          CollectingWriteStream dst = new CollectingWriteStream();

          factory
              .newPipe(src)
              .to(
                  dst,
                  ar -> {
                    testContext.assertTrue(ar.succeeded());
                    testContext.assertEquals(2, dst.received.size());
                    testContext.assertEquals("hello", dst.received.get(0).toString());
                    testContext.assertEquals("world", dst.received.get(1).toString());
                    testContext.assertEquals(
                        10L, registry.meter("HttpDown[BytesRead]").getCount());
                    testContext.assertEquals(
                        1L, registry.counter("HttpDown[NewPipeCnt]").getCount());
                    async.countDown();
                  });

          src.emit(Buffer.buffer("hello"));
          src.emit(Buffer.buffer("world"));
          src.end();
        });
  }

  @Test
  public void exceptionOnSourceFailsCompletion(TestContext testContext) {
    Vertx vertx = rule.vertx();
    Async async = testContext.async();
    vertx.runOnContext(
        v -> {
          MetricRegistry registry = new MetricRegistry();
          MetricPipeFactory factory =
              new MetricPipeFactory(registry, StreamType.HTTPS_UP, false, false);

          FakeReadStream src = new FakeReadStream();
          CollectingWriteStream dst = new CollectingWriteStream();

          factory
              .newPipe(src)
              .to(
                  dst,
                  ar -> {
                    testContext.assertTrue(ar.failed());
                    testContext.assertEquals("boom", ar.cause().getMessage());
                    async.countDown();
                  });

          src.fail(new RuntimeException("boom"));
        });
  }

  private static final class FakeReadStream implements ReadStream<Buffer> {
    private Handler<Buffer> handler;
    private Handler<Void> endHandler;
    private Handler<Throwable> exceptionHandler;

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
      if (handler != null) {
        handler.handle(buffer);
      }
    }

    void end() {
      if (endHandler != null) {
        endHandler.handle(null);
      }
    }

    void fail(Throwable cause) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(cause);
      }
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
