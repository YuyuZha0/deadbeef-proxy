package org.deadbeef.client;

import com.codahale.metrics.MetricRegistry;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.time.Duration;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.route.OriginProvider;
import org.deadbeef.util.Constants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class HttpProxyHandlerTest {

  @Rule public RunTestOnContext rule = new RunTestOnContext();

  private Future<HttpServer> startServer(
      Vertx vertx, io.vertx.core.Handler<io.vertx.core.http.HttpServerRequest> handler) {
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(handler);
    return server.listen(0).map(server);
  }

  private Future<HttpServer> startClientFacingServer(
      Vertx vertx, HttpClient httpClient, int remotePort, boolean proxyAll) {
    HttpProxyHandler handler =
        new HttpProxyHandler(
            vertx,
            httpClient,
            OriginProvider.ofStatic(remotePort, "127.0.0.1"),
            OriginProvider.ofAuthority(80),
            new ReachabilityGate<>(Duration.ofMinutes(5), 1_000),
            proxyAll,
            new ProxyAuthenticationGenerator("id", "key"),
            new org.deadbeef.metrics.ProxyMetrics(new MetricRegistry()));
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(handler);
    return server.listen(0).map(server);
  }

  /** A request sent to the facing proxy whose Host header points at {@code target}. */
  private io.vertx.core.http.RequestOptions browserRequest(
      SocketAddress facing, String targetHost, int targetPort) {
    return new io.vertx.core.http.RequestOptions()
        .setServer(facing)
        .setHost(targetHost)
        .setPort(targetPort)
        .setURI("/");
  }

  @Test
  public void servesDirectlyFromOriginWithoutAuthHeader(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // Direct origin: must NOT see the proxy auth header.
    startServer(
            vertx,
            req -> {
              ctx.assertNull(req.getHeader(Constants.authHeaderName()));
              req.response().end("hello");
            })
        .onFailure(ctx::fail)
        .onSuccess(
            origin -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(vertx, httpClient, 0, false)
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        SocketAddress facingAddr =
                            SocketAddress.inetSocketAddress(facing.actualPort(), "127.0.0.1");
                        HttpClient browser = vertx.createHttpClient();
                        browser
                            .request(browserRequest(facingAddr, "127.0.0.1", origin.actualPort()))
                            .compose(req -> req.send())
                            .compose(
                                resp -> {
                                  ctx.assertEquals(200, resp.statusCode());
                                  return resp.body();
                                })
                            .onSuccess(
                                body -> {
                                  ctx.assertEquals("hello", body.toString());
                                  done.complete();
                                })
                            .onFailure(ctx::fail);
                      });
            });
  }

  @Test
  public void fallsBackToRemoteWhenDirectConnectFails(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // Remote proxy stub: asserts it received a POST carrying the auth header, then rejects with 502
    // (a value the client surfaces verbatim without needing the protobuf round-trip).
    startServer(
            vertx,
            req -> {
              ctx.assertEquals(HttpMethod.POST, req.method());
              String token = req.getHeader(Constants.authHeaderName());
              ctx.assertNotNull(token);
              req.response().setStatusCode(502).end();
            })
        .onFailure(ctx::fail)
        .onSuccess(
            remote -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(vertx, httpClient, remote.actualPort(), false)
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        SocketAddress facingAddr =
                            SocketAddress.inetSocketAddress(facing.actualPort(), "127.0.0.1");
                        HttpClient browser = vertx.createHttpClient();
                        // Direct target 127.0.0.1:1 — nothing listens, so direct connect fails.
                        browser
                            .request(browserRequest(facingAddr, "127.0.0.1", 1))
                            .compose(req -> req.send())
                            .onSuccess(
                                resp -> {
                                  ctx.assertEquals(502, resp.statusCode());
                                  done.complete();
                                })
                            .onFailure(ctx::fail);
                      });
            });
  }

  @Test
  public void proxyAllSkipsDirectAndUsesRemote(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // With proxyAll, even a reachable origin must be ignored — the request goes to the remote stub.
    startServer(
            vertx,
            req -> {
              ctx.assertEquals(HttpMethod.POST, req.method());
              ctx.assertNotNull(req.getHeader(Constants.authHeaderName()));
              req.response().setStatusCode(502).end();
            })
        .onFailure(ctx::fail)
        .onSuccess(
            remote -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(vertx, httpClient, remote.actualPort(), true)
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        SocketAddress facingAddr =
                            SocketAddress.inetSocketAddress(facing.actualPort(), "127.0.0.1");
                        HttpClient browser = vertx.createHttpClient();
                        browser
                            .request(browserRequest(facingAddr, "127.0.0.1", 65000))
                            .compose(req -> req.send())
                            .onSuccess(
                                resp -> {
                                  ctx.assertEquals(502, resp.statusCode());
                                  done.complete();
                                })
                            .onFailure(ctx::fail);
                      });
            });
  }
}
