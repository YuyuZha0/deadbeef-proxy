package org.deadbeef.client;

import com.codahale.metrics.MetricRegistry;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
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
public class ConnectTunnelHandlerTest {

  @Rule public RunTestOnContext rule = new RunTestOnContext();

  /** Stub proxy server: behaviour decided by {@code onConnect}. */
  private Future<HttpServer> startStubServer(
      Vertx vertx, io.vertx.core.Handler<io.vertx.core.http.HttpServerRequest> onConnect) {
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(
        req -> {
          if (req.method() == HttpMethod.CONNECT) {
            onConnect.handle(req);
          } else {
            req.response().setStatusCode(404).end();
          }
        });
    return server.listen(0).map(server);
  }

  /** Raw TCP echo server, used as a "direct" upstream target. */
  private Future<NetServer> startEchoServer(Vertx vertx) {
    NetServer server = vertx.createNetServer();
    server.connectHandler(sock -> sock.handler(sock::write));
    return server.listen(0).map(server);
  }

  /** Spin up a local HttpServer hosting the ConnectTunnelHandler under test. */
  private Future<HttpServer> startClientFacingServer(
      Vertx vertx, HttpClient httpClient, NetClient netClient, int remotePort, boolean proxyAll) {
    ConnectTunnelHandler handler =
        new ConnectTunnelHandler(
            httpClient,
            netClient,
            OriginProvider.ofStatic(remotePort, "127.0.0.1"),
            OriginProvider.ofAuthority(443),
            new ReachabilityGate<>(Duration.ofMinutes(5), 1_000),
            proxyAll,
            new ProxyAuthenticationGenerator("id", "key"),
            new org.deadbeef.metrics.ProxyMetrics(new MetricRegistry()));
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(handler);
    return server.listen(0).map(server);
  }

  private Future<HttpServer> startClientFacingServer(
      Vertx vertx, HttpClient httpClient, int remotePort) {
    // Default helper for remote-path tests: always proxy, never try direct.
    return startClientFacingServer(
        vertx, httpClient, vertx.createNetClient(), remotePort, true);
  }

  @Test
  public void forwardsAuthHeaderToStubServer(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // The stub proxy: asserts auth header is present, then sends 200 + acts as echo upstream.
    startStubServer(
            vertx,
            req -> {
              String token = req.getHeader(Constants.authHeaderName());
              if (token == null || token.isEmpty()) {
                req.response().setStatusCode(407).end();
                return;
              }
              req.toNetSocket()
                  .onSuccess(sock -> sock.handler(sock::write))
                  .onFailure(ctx::fail);
            })
        .onFailure(ctx::fail)
        .onSuccess(
            stub -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(vertx, httpClient, stub.actualPort())
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        HttpClient browser = vertx.createHttpClient();
                        browser
                            .request(
                                new io.vertx.core.http.RequestOptions()
                                    .setMethod(HttpMethod.CONNECT)
                                    .setHost("127.0.0.1")
                                    .setPort(facing.actualPort())
                                    .setURI("example.com:443"))
                            .compose(req -> req.connect())
                            .onSuccess(
                                resp -> {
                                  ctx.assertEquals(HttpResponseStatus.OK.code(), resp.statusCode());
                                  NetSocket sock = resp.netSocket();
                                  Buffer received = Buffer.buffer();
                                  sock.handler(
                                      b -> {
                                        received.appendBuffer(b);
                                        if (received.toString().equals("ping")) {
                                          done.complete();
                                        }
                                      });
                                  sock.write("ping");
                                })
                            .onFailure(ctx::fail);
                      });
            });
  }

  @Test
  public void propagatesUpstreamErrorStatusToBrowser(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // Stub proxy always returns 502.
    startStubServer(vertx, req -> req.response().setStatusCode(502).end("bad upstream"))
        .onFailure(ctx::fail)
        .onSuccess(
            stub -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(vertx, httpClient, stub.actualPort())
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        HttpClient browser = vertx.createHttpClient();
                        browser
                            .request(
                                new io.vertx.core.http.RequestOptions()
                                    .setMethod(HttpMethod.CONNECT)
                                    .setHost("127.0.0.1")
                                    .setPort(facing.actualPort())
                                    .setURI("example.com:443"))
                            .compose(req -> req.connect())
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
  public void rejectsNonConnectMethod(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    HttpClient httpClient = vertx.createHttpClient();
    startClientFacingServer(vertx, httpClient, 0)
        .onFailure(ctx::fail)
        .onSuccess(
            facing -> {
              HttpClient browser = vertx.createHttpClient();
              browser
                  .request(
                      new io.vertx.core.http.RequestOptions()
                          .setMethod(HttpMethod.GET)
                          .setHost("127.0.0.1")
                          .setPort(facing.actualPort())
                          .setURI("/"))
                  .compose(req -> req.send())
                  .onSuccess(
                      resp -> {
                        ctx.assertEquals(400, resp.statusCode());
                        done.complete();
                      })
                  .onFailure(ctx::fail);
            });
  }

  @Test
  public void tunnelsDirectlyToTargetWithoutProxy(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // No remote stub at all; the direct echo server is the CONNECT target.
    startEchoServer(vertx)
        .onFailure(ctx::fail)
        .onSuccess(
            echo -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(vertx, httpClient, vertx.createNetClient(), 0, false)
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        HttpClient browser = vertx.createHttpClient();
                        browser
                            .request(
                                new io.vertx.core.http.RequestOptions()
                                    .setMethod(HttpMethod.CONNECT)
                                    .setHost("127.0.0.1")
                                    .setPort(facing.actualPort())
                                    .setURI("127.0.0.1:" + echo.actualPort()))
                            .compose(req -> req.connect())
                            .onSuccess(
                                resp -> {
                                  ctx.assertEquals(HttpResponseStatus.OK.code(), resp.statusCode());
                                  NetSocket sock = resp.netSocket();
                                  Buffer received = Buffer.buffer();
                                  sock.handler(
                                      b -> {
                                        received.appendBuffer(b);
                                        if (received.toString().equals("ping")) {
                                          done.complete();
                                        }
                                      });
                                  sock.write("ping");
                                })
                            .onFailure(ctx::fail);
                      });
            });
  }

  @Test
  public void fallsBackToRemoteWhenDirectConnectFails(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // Remote stub echoes; the direct target is a dead port, forcing the fallback path.
    startStubServer(
            vertx,
            req ->
                req.toNetSocket()
                    .onSuccess(sock -> sock.handler(sock::write))
                    .onFailure(ctx::fail))
        .onFailure(ctx::fail)
        .onSuccess(
            stub -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(vertx, httpClient, vertx.createNetClient(), stub.actualPort(), false)
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        HttpClient browser = vertx.createHttpClient();
                        browser
                            .request(
                                new io.vertx.core.http.RequestOptions()
                                    .setMethod(HttpMethod.CONNECT)
                                    .setHost("127.0.0.1")
                                    .setPort(facing.actualPort())
                                    // 127.0.0.1:1 — nothing listens here, so direct connect fails.
                                    .setURI("127.0.0.1:1"))
                            .compose(req -> req.connect())
                            .onSuccess(
                                resp -> {
                                  ctx.assertEquals(HttpResponseStatus.OK.code(), resp.statusCode());
                                  NetSocket sock = resp.netSocket();
                                  Buffer received = Buffer.buffer();
                                  sock.handler(
                                      b -> {
                                        received.appendBuffer(b);
                                        if (received.toString().equals("ping")) {
                                          done.complete();
                                        }
                                      });
                                  sock.write("ping");
                                })
                            .onFailure(ctx::fail);
                      });
            });
  }
}
