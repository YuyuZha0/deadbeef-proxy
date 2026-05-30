package org.deadbeef.client;

import com.codahale.metrics.MetricRegistry;
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
import org.deadbeef.route.RoutePolicy;
import org.deadbeef.util.Constants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/**
 * Tests the handler's dispatch for each {@link RoutePolicy.Decision}. RoutePolicy is mocked, so
 * these tests do not depend on the real routing logic (that lives in {@code DefaultRoutePolicyTest}).
 */
@RunWith(VertxUnitRunner.class)
public class HttpProxyHandlerTest {

  @Rule public RunTestOnContext rule = new RunTestOnContext();

  /** A RoutePolicy stub that always returns {@code decision} for any host. */
  private static RoutePolicy decision(RoutePolicy.Decision decision) {
    RoutePolicy policy = Mockito.mock(RoutePolicy.class);
    Mockito.when(policy.decide(Mockito.anyString())).thenReturn(decision);
    return policy;
  }

  private Future<HttpServer> startServer(
      Vertx vertx, io.vertx.core.Handler<io.vertx.core.http.HttpServerRequest> handler) {
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(handler);
    return server.listen(0).map(server);
  }

  private Future<HttpServer> startClientFacingServer(
      Vertx vertx, HttpClient httpClient, int remotePort, RoutePolicy routePolicy) {
    // localPort 0 is never a real target port, so the self-target guard never fires here.
    return startClientFacingServer(vertx, httpClient, remotePort, 0, routePolicy);
  }

  private Future<HttpServer> startClientFacingServer(
      Vertx vertx, HttpClient httpClient, int remotePort, int localPort, RoutePolicy routePolicy) {
    HttpProxyHandler handler =
        new HttpProxyHandler(
            vertx,
            httpClient,
            OriginProvider.ofStatic(remotePort, "127.0.0.1"),
            OriginProvider.ofAuthority(80),
            new ReachabilityGate<>(Duration.ofMinutes(5), 1_000),
            routePolicy,
            localPort,
            new ProxyAuthenticationGenerator("id", "key"),
            new org.deadbeef.metrics.ProxyMetrics(new MetricRegistry()));
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(handler);
    return server.listen(0).map(server);
  }

  private io.vertx.core.http.RequestOptions browserRequest(
      SocketAddress facing, String targetHost, int targetPort) {
    return new io.vertx.core.http.RequestOptions()
        .setServer(facing)
        .setHost(targetHost)
        .setPort(targetPort)
        .setURI("/");
  }

  @Test
  public void directDecisionServesFromOriginWithoutAuthHeader(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    startServer(
            vertx,
            req -> {
              ctx.assertNull(req.getHeader(Constants.authHeaderName())); // direct: no proxy auth
              req.response().end("hello");
            })
        .onFailure(ctx::fail)
        .onSuccess(
            origin -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(vertx, httpClient, 0, decision(RoutePolicy.Decision.DIRECT))
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        SocketAddress facingAddr =
                            SocketAddress.inetSocketAddress(facing.actualPort(), "127.0.0.1");
                        vertx
                            .createHttpClient()
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
  public void gateFallsBackToRemoteWhenDirectConnectFails(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // Remote stub asserts it received the protobuf POST + auth header, then rejects with 502.
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
              startClientFacingServer(
                      vertx, httpClient, remote.actualPort(), decision(RoutePolicy.Decision.GATE))
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        SocketAddress facingAddr =
                            SocketAddress.inetSocketAddress(facing.actualPort(), "127.0.0.1");
                        vertx
                            .createHttpClient()
                            // direct target 127.0.0.1:1 is refused -> gate falls back to remote
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
  public void remoteDecisionUsesRemoteProxyEvenWhenDirectReachable(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // Reachable origin that would answer 200 if direct were taken.
    startServer(vertx, req -> req.response().end("DIRECT"))
        .onFailure(ctx::fail)
        .onSuccess(
            origin ->
                startServer(vertx, req -> req.response().setStatusCode(502).end())
                    .onFailure(ctx::fail)
                    .onSuccess(
                        remote -> {
                          HttpClient httpClient = vertx.createHttpClient();
                          startClientFacingServer(
                                  vertx,
                                  httpClient,
                                  remote.actualPort(),
                                  decision(RoutePolicy.Decision.REMOTE))
                              .onFailure(ctx::fail)
                              .onSuccess(
                                  facing -> {
                                    SocketAddress facingAddr =
                                        SocketAddress.inetSocketAddress(
                                            facing.actualPort(), "127.0.0.1");
                                    vertx
                                        .createHttpClient()
                                        .request(
                                            browserRequest(
                                                facingAddr, "127.0.0.1", origin.actualPort()))
                                        .compose(req -> req.send())
                                        .onSuccess(
                                            resp -> {
                                              // 502 from the remote stub, not 200 from the origin.
                                              ctx.assertEquals(502, resp.statusCode());
                                              done.complete();
                                            })
                                        .onFailure(ctx::fail);
                                  });
                        }));
  }

  @Test
  public void directDecisionErrorsWithoutFallback(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // Remote proxy must NOT be contacted for a DIRECT (hard-pinned) decision.
    startServer(vertx, req -> ctx.fail("remote proxy must not be used for a DIRECT decision"))
        .onFailure(ctx::fail)
        .onSuccess(
            remote -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(
                      vertx, httpClient, remote.actualPort(), decision(RoutePolicy.Decision.DIRECT))
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        SocketAddress facingAddr =
                            SocketAddress.inetSocketAddress(facing.actualPort(), "127.0.0.1");
                        vertx
                            .createHttpClient()
                            .request(browserRequest(facingAddr, "127.0.0.1", 1)) // dead direct
                            .compose(req -> req.send())
                            .onSuccess(
                                resp -> {
                                  ctx.assertEquals(502, resp.statusCode()); // BAD_GATEWAY error
                                  done.complete();
                                })
                            .onFailure(ctx::fail);
                      });
            });
  }

  @Test
  public void selfTargetIsRejectedWithLoopDetected(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    int localPort = 7777;
    // Neither the origin (direct) nor the remote proxy must be contacted for a self-target.
    startServer(vertx, req -> ctx.fail("self-target must not be routed anywhere"))
        .onFailure(ctx::fail)
        .onSuccess(
            remote -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(
                      vertx,
                      httpClient,
                      remote.actualPort(),
                      localPort,
                      decision(RoutePolicy.Decision.DIRECT))
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        SocketAddress facingAddr =
                            SocketAddress.inetSocketAddress(facing.actualPort(), "127.0.0.1");
                        vertx
                            .createHttpClient()
                            // target == the client's own loopback listen address
                            .request(browserRequest(facingAddr, "127.0.0.1", localPort))
                            .compose(req -> req.send())
                            .onSuccess(
                                resp -> {
                                  ctx.assertEquals(508, resp.statusCode());
                                  done.complete();
                                })
                            .onFailure(ctx::fail);
                      });
            });
  }

  @Test
  public void decideFailureFallsBackToRemote(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();

    // A RoutePolicy that throws (e.g. matcher closed at shutdown) must not hang the request.
    RoutePolicy throwing = Mockito.mock(RoutePolicy.class);
    Mockito.when(throwing.decide(Mockito.anyString()))
        .thenThrow(new IllegalStateException("policy unavailable"));

    startServer(vertx, req -> req.response().setStatusCode(502).end())
        .onFailure(ctx::fail)
        .onSuccess(
            remote -> {
              HttpClient httpClient = vertx.createHttpClient();
              startClientFacingServer(vertx, httpClient, remote.actualPort(), throwing)
                  .onFailure(ctx::fail)
                  .onSuccess(
                      facing -> {
                        SocketAddress facingAddr =
                            SocketAddress.inetSocketAddress(facing.actualPort(), "127.0.0.1");
                        vertx
                            .createHttpClient()
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
