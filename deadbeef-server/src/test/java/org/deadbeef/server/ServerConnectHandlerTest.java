package org.deadbeef.server;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.auth.ProxyAuthenticationValidator;
import org.deadbeef.security.UpstreamAddressFilter;
import org.deadbeef.streams.DefaultPipeFactory;
import org.deadbeef.util.Constants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ServerConnectHandlerTest {

  private static final String SECRET_ID = "test-id";
  private static final String SECRET_KEY = "test-key";

  @Rule public RunTestOnContext rule = new RunTestOnContext();

  /**
   * Default test setup allows loopback so the fake upstream (bound to 127.0.0.1) can be reached.
   */
  private Future<HttpServer> startProxyServer(Vertx vertx) {
    return startProxyServer(vertx, UpstreamAddressFilter.builder().allowLoopback().build());
  }

  private Future<HttpServer> startProxyServer(Vertx vertx, UpstreamAddressFilter filter) {
    NetClient netClient = vertx.createNetClient();
    ProxyAuthenticationValidator validator =
        ProxyAuthenticationValidator.simple(SECRET_ID, SECRET_KEY);
    ServerConnectHandler handler =
        new ServerConnectHandler(vertx, netClient, validator, new DefaultPipeFactory(), filter);
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(
        req -> {
          if (req.method() == HttpMethod.CONNECT) {
            handler.handle(req);
          } else {
            req.response().setStatusCode(404).end();
          }
        });
    return server.listen(0).map(server);
  }

  @Test
  public void rejectsMissingAuthWith407(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    startProxyServer(vertx)
        .onFailure(ctx::fail)
        .onSuccess(
            proxy -> {
              HttpClient client = vertx.createHttpClient();
              client
                  .request(
                      new RequestOptions()
                          .setMethod(HttpMethod.CONNECT)
                          .setHost("127.0.0.1")
                          .setPort(proxy.actualPort())
                          .setURI("example.com:443"))
                  .compose(req -> req.connect())
                  .onSuccess(
                      resp -> {
                        ctx.assertEquals(407, resp.statusCode());
                        done.complete();
                      })
                  .onFailure(ctx::fail);
            });
  }

  @Test
  public void rejectsMalformedAuthorityWith400(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    String token = new ProxyAuthenticationGenerator(SECRET_ID, SECRET_KEY).getString();
    startProxyServer(vertx)
        .onFailure(ctx::fail)
        .onSuccess(
            proxy -> {
              HttpClient client = vertx.createHttpClient();
              client
                  .request(
                      new RequestOptions()
                          .setMethod(HttpMethod.CONNECT)
                          .setHost("127.0.0.1")
                          .setPort(proxy.actualPort())
                          .setURI("not a host port")
                          .putHeader(Constants.authHeaderName(), token))
                  .compose(req -> req.connect())
                  .onSuccess(
                      resp -> {
                        ctx.assertEquals(400, resp.statusCode());
                        done.complete();
                      })
                  .onFailure(ctx::fail);
            });
  }

  @Test
  public void happyPathRelaysBytes(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    String token = new ProxyAuthenticationGenerator(SECRET_ID, SECRET_KEY).getString();

    NetServer upstream = vertx.createNetServer();
    upstream.connectHandler(sock -> sock.handler(sock::write));
    upstream
        .listen(0)
        .onFailure(ctx::fail)
        .onSuccess(
            srv -> {
              int upstreamPort = srv.actualPort();
              startProxyServer(vertx)
                  .onFailure(ctx::fail)
                  .onSuccess(
                      proxy -> {
                        HttpClient client = vertx.createHttpClient();
                        client
                            .request(
                                new RequestOptions()
                                    .setMethod(HttpMethod.CONNECT)
                                    .setHost("127.0.0.1")
                                    .setPort(proxy.actualPort())
                                    .setURI("127.0.0.1:" + upstreamPort)
                                    .putHeader(Constants.authHeaderName(), token))
                            .compose(req -> req.connect())
                            .onSuccess(
                                resp -> {
                                  ctx.assertEquals(200, resp.statusCode());
                                  io.vertx.core.net.NetSocket tunnel = resp.netSocket();
                                  Buffer received = Buffer.buffer();
                                  tunnel.handler(
                                      b -> {
                                        received.appendBuffer(b);
                                        if (received.toString().equals("hello-world")) {
                                          done.complete();
                                        }
                                      });
                                  tunnel.write("hello-world");
                                })
                            .onFailure(ctx::fail);
                      });
            });
  }

  // ---- new: default-deny filter rejects forbidden literal upstreams with 403 ----

  @Test
  public void filterRejectsLoopbackLiteralWith403(TestContext ctx) {
    runForbiddenLiteralTest(ctx, "127.0.0.1:9");
  }

  @Test
  public void filterRejectsLinkLocalMetadataWith403(TestContext ctx) {
    runForbiddenLiteralTest(ctx, "169.254.169.254:80");
  }

  @Test
  public void filterRejectsRfc1918Rangewith403(TestContext ctx) {
    runForbiddenLiteralTest(ctx, "10.0.0.1:80");
  }

  @Test
  public void filterRejectsAnyLocalWith403(TestContext ctx) {
    runForbiddenLiteralTest(ctx, "0.0.0.0:80");
  }

  private void runForbiddenLiteralTest(TestContext ctx, String authority) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    String token = new ProxyAuthenticationGenerator(SECRET_ID, SECRET_KEY).getString();
    // Use strict default filter (no loopback allowance).
    startProxyServer(vertx, UpstreamAddressFilter.defaultDenyList())
        .onFailure(ctx::fail)
        .onSuccess(
            proxy -> {
              HttpClient client = vertx.createHttpClient();
              client
                  .request(
                      new RequestOptions()
                          .setMethod(HttpMethod.CONNECT)
                          .setHost("127.0.0.1")
                          .setPort(proxy.actualPort())
                          .setURI(authority)
                          .putHeader(Constants.authHeaderName(), token))
                  .compose(req -> req.connect())
                  .onSuccess(
                      resp -> {
                        ctx.assertEquals(403, resp.statusCode());
                        done.complete();
                      })
                  .onFailure(ctx::fail);
            });
  }
}
