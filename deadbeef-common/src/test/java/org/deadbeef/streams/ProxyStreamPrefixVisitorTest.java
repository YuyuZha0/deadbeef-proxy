package org.deadbeef.streams;

import com.google.common.base.Strings;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThreadLocalRandom;
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
import org.deadbeef.protocol.HttpProto;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(VertxUnitRunner.class)
public class ProxyStreamPrefixVisitorTest {

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
    ProxyStreamPrefixVisitor<HttpServerResponse> resolver = new ProxyStreamPrefixVisitor<>(vertx);
    httpServer.requestHandler(
        request -> {
          resolver
              .visit(request)
              .onSuccess(
                  prefixAndAction -> {
                    Buffer prefix = prefixAndAction.get();
                    try {
                      System.out.println(HttpProto.Request.parseFrom(prefix.getBytes()));
                    } catch (InvalidProtocolBufferException e) {
                      testContext.fail(e);
                    }
                    HttpServerResponse response = request.response();
                    String contentLen = request.getHeader(HttpHeaderNames.CONTENT_LENGTH);
                    response.putHeader(
                        HttpHeaderNames.CONTENT_LENGTH,
                        Integer.toString(Integer.parseInt(contentLen) - prefix.length() - 8));
                    prefixAndAction.accept(
                        response,
                        ar -> {
                          response.end();
                        });
                  })
              .onFailure(testContext::fail);
        });
    HttpClient httpClient = vertx.createHttpClient();
    httpServer
        .listen(port)
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                sendReq(vertx, httpClient, testContext, async);
              } else {
                testContext.fail(ar.cause());
              }
            });
  }

  private void sendReq(Vertx vertx, HttpClient httpClient, TestContext testContext, Async async) {
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
                  .onSuccess(
                      response -> {
                        //                        response.handler(
                        //                            buffer -> {
                        //                              System.out.println("Receive buffer len: " +
                        // buffer.length());
                        //                              System.out.println(response.headers());
                        //                            });
                        response
                            .body()
                            .onSuccess(
                                buffer -> {
                                  testContext.assertTrue(
                                      Arrays.equals(randomData.getBytes(), buffer.getBytes()),
                                      Strings.lenientFormat(
                                          "%s, %s", randomData.length(), buffer.length()));
                                  async.countDown();
                                })
                            .onFailure(testContext::fail);
                      })
                  .onFailure(testContext::fail);
            })
        .onFailure(testContext::fail);
  }
}
