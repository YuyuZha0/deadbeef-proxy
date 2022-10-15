package org.deadbeaf.protocol;

import com.google.common.base.Strings;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThreadLocalRandom;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(VertxUnitRunner.class)
public class ProxyStreamPrefixResolverTest {

  private final int port = 15367;
  @Rule public RunTestOnContext rule = new RunTestOnContext();

  private static Buffer generatePrefix() {
    return Prefix.serializeToBuffer(
        HttpProto.Request.newBuilder()
            .setHeaders(
                HttpProto.Headers.newBuilder().setAccept("*/*").setContentType("application/json"))
            .setMethod(HttpProto.Method.POST)
            .setScheme("https")
            .setVersion(HttpProto.Version.HTTP_1_1)
            .build());
  }

  private static Buffer randomBytes(int len) {
    byte[] bytes = PlatformDependent.allocateUninitializedArray(len);
    ThreadLocalRandom.current().nextBytes(bytes);
    return Buffer.buffer(bytes);
  }

  @Test
  public void test(TestContext testContext) {
    Vertx vertx = rule.vertx();
    Async async = testContext.async();
    HttpServer httpServer = vertx.createHttpServer();
    ProxyStreamPrefixResolver<HttpServerResponse> resolver = new ProxyStreamPrefixResolver<>(vertx);
    httpServer.requestHandler(
        request -> {
          resolver.resolvePrefix(
              request,
              data -> {
                try {
                  System.out.println(HttpProto.Request.parseFrom(data.getBytes()));
                } catch (InvalidProtocolBufferException e) {
                  throw new RuntimeException(e);
                }
                HttpServerResponse response = request.response();
                String contentLen = request.getHeader(HttpHeaderNames.CONTENT_LENGTH);
                response.putHeader(
                    HttpHeaderNames.CONTENT_LENGTH,
                    Integer.toString(Integer.parseInt(contentLen) - data.length() - 8));
                // .putHeader(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                return Future.succeededFuture(response);
              },
              r -> {
                r.end().onComplete(ar -> r.close());
                System.out.printf("Ended: %s, bytesWritten: %d%n", r.ended(), r.bytesWritten());
              });
        });
    HttpClient httpClient = vertx.createHttpClient();
    httpServer
        .listen(port)
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                sendReq(httpClient, testContext, async);
              } else {
                testContext.fail(ar.cause());
              }
            });
  }

  private void sendReq(HttpClient httpClient, TestContext testContext, Async async) {
    httpClient
        .request(HttpMethod.POST, port, "127.0.0.1", "")
        .onSuccess(
            request -> {
              Buffer prefix = generatePrefix();
              Buffer randomData = randomBytes(1 << 12);
              System.out.printf(
                  "%d = %d + %d;%n",
                  prefix.length() + randomData.length(), prefix.length(), randomData.length());
              request.putHeader(
                  HttpHeaderNames.CONTENT_LENGTH,
                  Integer.toString(prefix.length() + randomData.length()));
              request.write(prefix);
              request
                  .end(randomData)
                  .onSuccess(
                      v ->
                          request
                              .response()
                              .onSuccess(
                                  response -> {
                                    response.handler(
                                        buffer -> {
                                          System.out.println(
                                              "Receive buffer len: " + buffer.length());
                                          System.out.println(response.headers());
                                        });
                                    response
                                        .body()
                                        .onSuccess(
                                            buffer -> {
                                              testContext.assertTrue(
                                                  Arrays.equals(
                                                      randomData.getBytes(), buffer.getBytes()),
                                                  Strings.lenientFormat(
                                                      "%s, %s",
                                                      randomData.length(), buffer.length()));
                                              async.countDown();
                                            })
                                        .onFailure(testContext::fail);
                                  })
                              .onFailure(testContext::fail))
                  .onFailure(testContext::fail);
            })
        .onFailure(testContext::fail);
  }
}
