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
import org.deadbeef.route.RoutePolicy;
import org.deadbeef.util.Constants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/**
 * Tests the handler's dispatch for each {@link RoutePolicy.Decision}. RoutePolicy is mocked, so
 * these tests do not depend on the real routing logic (that lives in {@code
 * DefaultRoutePolicyTest}).
 */
@RunWith(VertxUnitRunner.class)
public class ConnectTunnelHandlerTest {

  @Rule public RunTestOnContext rule = new RunTestOnContext();

  /** A RoutePolicy stub that always returns {@code decision} for any host. */
  private static RoutePolicy decision(RoutePolicy.Decision decision) {
    RoutePolicy policy = Mockito.mock(RoutePolicy.class);
    Mockito.when(policy.decide(Mockito.anyString())).thenReturn(decision);
    return policy;
  }

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

  /** Raw TCP echo server, used as a direct or remote-tunnel upstream. */
  private Future<NetServer> startEchoServer(Vertx vertx) {
    NetServer server = vertx.createNetServer();
    server.connectHandler(sock -> sock.handler(sock::write));
    return server.listen(0).map(server);
  }

  private Future<HttpServer> startClientFacingServer(
      Vertx vertx, HttpClient httpClient, NetClient netClient, int remotePort, RoutePolicy policy) {
    // localPort 0 is never a real target port, so the self-target guard never fires here.
    return startClientFacingServer(vertx, httpClient, netClient, remotePort, 0, policy);
  }

  private Future<HttpServer> startClientFacingServer(
      Vertx vertx,
      HttpClient httpClient,
      NetClient netClient,
      int remotePort,
      int localPort,
      RoutePolicy policy) {
    ConnectTunnelHandler handler =
        new ConnectTunnelHandler(
            httpClient,
            netClient,
            OriginProvider.ofStatic(remotePort, "127.0.0.1"),
            OriginProvider.ofAuthority(443),
            new ReachabilityGate<>(Duration.ofMinutes(5), 1_000),
            policy,
            localPort,
            new ProxyAuthenticationGenerator("id", "key"),
            new org.deadbeef.metrics.ProxyMetrics(new MetricRegistry()));
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(handler);
    return server.listen(0).map(server);
  }

  private io.vertx.core.http.RequestOptions connectRequest(HttpServer facing, String authority) {
    return new io.vertx.core.http.RequestOptions()
        .setMethod(HttpMethod.CONNECT)
        .setHost("127.0.0.1")
        .setPort(facing.actualPort())
        .setURI(authority);
  }

  /** Drive a CONNECT, then echo "ping" through the tunnel and complete when it comes back. */
  private void expectEchoTunnel(
      TestContext ctx, Async done, HttpClient browser, HttpServer facing, String authority) {
    browser
        .request(connectRequest(facing, authority))
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
  }

  @Test
  public void directDecisionTunnelsToTarget(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    startEchoServer(vertx)
        .onFailure(ctx::fail)
        .onSuccess(
            echo ->
                startClientFacingServer(
                        vertx,
                        vertx.createHttpClient(),
                        vertx.createNetClient(),
                        0,
                        decision(RoutePolicy.Decision.DIRECT))
                    .onFailure(ctx::fail)
                    .onSuccess(
                        facing ->
                            expectEchoTunnel(
                                ctx,
                                done,
                                vertx.createHttpClient(),
                                facing,
                                "127.0.0.1:" + echo.actualPort())));
  }

  @Test
  public void gateFallsBackToRemoteWhenDirectConnectFails(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    startStubServer(
            vertx,
            req ->
                req.toNetSocket().onSuccess(sock -> sock.handler(sock::write)).onFailure(ctx::fail))
        .onFailure(ctx::fail)
        .onSuccess(
            stub ->
                startClientFacingServer(
                        vertx,
                        vertx.createHttpClient(),
                        vertx.createNetClient(),
                        stub.actualPort(),
                        decision(RoutePolicy.Decision.GATE))
                    .onFailure(ctx::fail)
                    .onSuccess(
                        facing ->
                            // direct target 127.0.0.1:1 is refused -> gate falls back to remote
                            expectEchoTunnel(
                                ctx, done, vertx.createHttpClient(), facing, "127.0.0.1:1")));
  }

  @Test
  public void remoteDecisionForwardsAuthHeaderAndTunnels(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    startStubServer(
            vertx,
            req -> {
              String token = req.getHeader(Constants.authHeaderName());
              if (token == null || token.isEmpty()) {
                req.response().setStatusCode(407).end();
                return;
              }
              req.toNetSocket().onSuccess(sock -> sock.handler(sock::write)).onFailure(ctx::fail);
            })
        .onFailure(ctx::fail)
        .onSuccess(
            stub ->
                startClientFacingServer(
                        vertx,
                        vertx.createHttpClient(),
                        vertx.createNetClient(),
                        stub.actualPort(),
                        decision(RoutePolicy.Decision.REMOTE))
                    .onFailure(ctx::fail)
                    .onSuccess(
                        facing ->
                            expectEchoTunnel(
                                ctx, done, vertx.createHttpClient(), facing, "example.com:443")));
  }

  @Test
  public void remoteDecisionPropagatesUpstreamErrorStatusToBrowser(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    startStubServer(vertx, req -> req.response().setStatusCode(502).end("bad upstream"))
        .onFailure(ctx::fail)
        .onSuccess(
            stub ->
                startClientFacingServer(
                        vertx,
                        vertx.createHttpClient(),
                        vertx.createNetClient(),
                        stub.actualPort(),
                        decision(RoutePolicy.Decision.REMOTE))
                    .onFailure(ctx::fail)
                    .onSuccess(
                        facing ->
                            vertx
                                .createHttpClient()
                                .request(connectRequest(facing, "example.com:443"))
                                .compose(req -> req.connect())
                                .onSuccess(
                                    resp -> {
                                      ctx.assertEquals(502, resp.statusCode());
                                      done.complete();
                                    })
                                .onFailure(ctx::fail)));
  }

  @Test
  public void directDecisionErrorsWithoutFallback(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    // Remote proxy must NOT be contacted for a DIRECT decision.
    startStubServer(vertx, req -> ctx.fail("remote proxy must not be used for a DIRECT decision"))
        .onFailure(ctx::fail)
        .onSuccess(
            stub ->
                startClientFacingServer(
                        vertx,
                        vertx.createHttpClient(),
                        vertx.createNetClient(),
                        stub.actualPort(),
                        decision(RoutePolicy.Decision.DIRECT))
                    .onFailure(ctx::fail)
                    .onSuccess(
                        facing ->
                            vertx
                                .createHttpClient()
                                .request(connectRequest(facing, "127.0.0.1:1")) // dead direct
                                .compose(req -> req.connect())
                                .onSuccess(
                                    resp -> {
                                      ctx.assertEquals(
                                          HttpResponseStatus.BAD_GATEWAY.code(), resp.statusCode());
                                      done.complete();
                                    })
                                .onFailure(ctx::fail)));
  }

  @Test
  public void selfTargetIsRejectedWithLoopDetected(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    int localPort = 7777;
    // The remote proxy must NOT be contacted for a self-target.
    startStubServer(vertx, req -> ctx.fail("self-target must not be routed anywhere"))
        .onFailure(ctx::fail)
        .onSuccess(
            stub ->
                startClientFacingServer(
                        vertx,
                        vertx.createHttpClient(),
                        vertx.createNetClient(),
                        stub.actualPort(),
                        localPort,
                        decision(RoutePolicy.Decision.DIRECT))
                    .onFailure(ctx::fail)
                    .onSuccess(
                        facing ->
                            vertx
                                .createHttpClient()
                                // target == the client's own loopback listen address
                                .request(connectRequest(facing, "127.0.0.1:" + localPort))
                                .compose(req -> req.connect())
                                .onSuccess(
                                    resp -> {
                                      ctx.assertEquals(508, resp.statusCode());
                                      done.complete();
                                    })
                                .onFailure(ctx::fail)));
  }

  @Test
  public void rejectsNonConnectMethod(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    startClientFacingServer(
            vertx,
            vertx.createHttpClient(),
            vertx.createNetClient(),
            0,
            decision(RoutePolicy.Decision.GATE))
        .onFailure(ctx::fail)
        .onSuccess(
            facing ->
                vertx
                    .createHttpClient()
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
                    .onFailure(ctx::fail));
  }

  @Test
  public void malformedAuthorityIsHandledNotHung(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    // A malformed authority makes targetProvider.apply throw before decide() is reached; the
    // handler
    // must catch it and hand off to the remote proxy rather than leak the exception and hang.
    startStubServer(vertx, req -> req.response().setStatusCode(502).end())
        .onFailure(ctx::fail)
        .onSuccess(
            stub ->
                startClientFacingServer(
                        vertx,
                        vertx.createHttpClient(),
                        vertx.createNetClient(),
                        stub.actualPort(),
                        decision(RoutePolicy.Decision.GATE))
                    .onFailure(ctx::fail)
                    .onSuccess(
                        facing ->
                            vertx
                                .createHttpClient()
                                .request(connectRequest(facing, "a:b:c")) // unparseable authority
                                .compose(req -> req.connect())
                                .onSuccess(
                                    resp -> {
                                      ctx.assertEquals(502, resp.statusCode());
                                      done.complete();
                                    })
                                .onFailure(ctx::fail)));
  }

  @Test
  public void decideFailureFallsBackToRemote(TestContext ctx) {
    Vertx vertx = rule.vertx();
    Async done = ctx.async();
    RoutePolicy throwing = Mockito.mock(RoutePolicy.class);
    Mockito.when(throwing.decide(Mockito.anyString()))
        .thenThrow(new IllegalStateException("policy unavailable"));

    startStubServer(vertx, req -> req.response().setStatusCode(502).end())
        .onFailure(ctx::fail)
        .onSuccess(
            stub ->
                startClientFacingServer(
                        vertx,
                        vertx.createHttpClient(),
                        vertx.createNetClient(),
                        stub.actualPort(),
                        throwing)
                    .onFailure(ctx::fail)
                    .onSuccess(
                        facing ->
                            vertx
                                .createHttpClient()
                                .request(connectRequest(facing, "example.com:443"))
                                .compose(req -> req.connect())
                                .onSuccess(
                                    resp -> {
                                      ctx.assertEquals(502, resp.statusCode());
                                      done.complete();
                                    })
                                .onFailure(ctx::fail)));
  }
}
