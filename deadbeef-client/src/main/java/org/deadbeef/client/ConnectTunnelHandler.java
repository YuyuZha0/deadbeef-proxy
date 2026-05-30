package org.deadbeef.client;

import com.codahale.metrics.Timer;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.metrics.ProxyMetrics;
import org.deadbeef.route.HostNameMatcher;
import org.deadbeef.route.OriginProvider;
import org.deadbeef.streams.MetricPipeFactory;
import org.deadbeef.streams.PipeFactory;
import org.deadbeef.streams.Tunnels;
import org.deadbeef.util.Constants;
import org.deadbeef.util.HttpRequestUtils;
import org.deadbeef.util.Utils;

/**
 * Handles browser {@code CONNECT} (HTTPS tunnel) requests. Unless {@code proxyAll} is set, it first
 * tries a raw TCP tunnel straight to the target ({@code netClient}), gated by {@link
 * ReachabilityGate}; only when the direct connection cannot be established does it fall back to
 * tunnelling through the remote proxy via the {@code CONNECT}-over-HTTP protocol.
 */
@Slf4j
public final class ConnectTunnelHandler implements Handler<HttpServerRequest> {

  private final HttpClient httpClient;
  private final NetClient netClient;
  private final OriginProvider remoteProvider;
  private final OriginProvider targetProvider;
  private final ReachabilityGate<NetSocket> reachabilityGate;
  private final HostNameMatcher localOnly;
  private final HostNameMatcher remoteOnly;
  private final boolean proxyAll;
  private final ProxyAuthenticationGenerator generator;
  private final ProxyMetrics metrics;
  private final PipeFactory upPipeFactory;
  private final PipeFactory downPipeFactory;

  public ConnectTunnelHandler(
      @NonNull HttpClient httpClient,
      @NonNull NetClient netClient,
      @NonNull OriginProvider remoteProvider,
      @NonNull OriginProvider targetProvider,
      @NonNull ReachabilityGate<NetSocket> reachabilityGate,
      @NonNull HostNameMatcher localOnly,
      @NonNull HostNameMatcher remoteOnly,
      boolean proxyAll,
      @NonNull ProxyAuthenticationGenerator generator,
      @NonNull ProxyMetrics metrics) {
    this.httpClient = httpClient;
    this.netClient = netClient;
    this.remoteProvider = remoteProvider;
    this.targetProvider = targetProvider;
    this.reachabilityGate = reachabilityGate;
    this.localOnly = localOnly;
    this.remoteOnly = remoteOnly;
    this.proxyAll = proxyAll;
    this.generator = generator;
    this.metrics = metrics;
    this.upPipeFactory = new MetricPipeFactory(metrics.httpsBytesUp);
    this.downPipeFactory = new MetricPipeFactory(metrics.httpsBytesDown);
  }

  @Override
  public void handle(HttpServerRequest serverRequest) {
    if (serverRequest.method() != HttpMethod.CONNECT
        || StringUtils.isEmpty(serverRequest.getHeader(HttpHeaderNames.HOST))) {
      serverRequest.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
      return;
    }

    Handler<Throwable> originalErrorHandler =
        HttpRequestUtils.createErrorHandler(serverRequest.response());
    Timer.Context connectTimer = metrics.httpsConnectDuration.time();
    Handler<Void> stopConnectTimerOnce = Utils.atMostOnce(v -> connectTimer.stop());
    Handler<Throwable> errorHandler =
        Utils.atMostOnce(
            cause -> {
              metrics.httpsTunnelsFailed.inc();
              stopConnectTimerOnce.handle(null);
              originalErrorHandler.handle(cause);
            });

    serverRequest.pause();

    if (proxyAll) {
      tunnelViaRemote(serverRequest, stopConnectTimerOnce, errorHandler);
      return;
    }

    SocketAddress target = targetProvider.apply(serverRequest);
    String host = target.host();
    if (remoteOnly.match(host)) {
      // Known-blocked: skip the doomed direct attempt.
      tunnelViaRemote(serverRequest, stopConnectTimerOnce, errorHandler);
      return;
    }
    if (localOnly.match(host)) {
      // Hard-pinned direct: never use the remote proxy; a connect failure surfaces as an error.
      netClient
          .connect(target)
          .onSuccess(
              upstream -> {
                metrics.httpsDirectTunnels.inc();
                openTunnel(serverRequest, upstream, stopConnectTimerOnce, errorHandler);
              })
          .onFailure(errorHandler);
      return;
    }

    // Unlisted: try a direct TCP tunnel first; fall back to the remote proxy on connect failure.
    reachabilityGate
        .apply(target, () -> netClient.connect(target))
        .onSuccess(
            upstream -> {
              metrics.httpsDirectTunnels.inc();
              openTunnel(serverRequest, upstream, stopConnectTimerOnce, errorHandler);
            })
        .onFailure(cause -> tunnelViaRemote(serverRequest, stopConnectTimerOnce, errorHandler));
  }

  private void tunnelViaRemote(
      HttpServerRequest serverRequest, Handler<Void> stopTimer, Handler<Throwable> errorHandler) {
    SocketAddress remote = remoteProvider.apply(serverRequest);
    RequestOptions options =
        new RequestOptions()
            .setMethod(HttpMethod.CONNECT)
            .setServer(remote)
            .setHost(remote.host())
            .setPort(remote.port())
            .setURI(serverRequest.getHeader(HttpHeaderNames.HOST))
            .putHeader(Constants.authHeaderName(), generator.getString());

    httpClient
        .request(options)
        .onFailure(errorHandler)
        .onSuccess(
            clientRequest ->
                clientRequest
                    .connect()
                    .onFailure(errorHandler)
                    .onSuccess(
                        clientResponse -> {
                          int status = clientResponse.statusCode();
                          if (status != HttpResponseStatus.OK.code()) {
                            stopTimer.handle(null);
                            metrics.httpsTunnelsFailed.inc();
                            serverRequest
                                .response()
                                .setStatusCode(status)
                                .end(clientResponse.statusMessage());
                            return;
                          }
                          metrics.httpsRemoteTunnels.inc();
                          openTunnel(
                              serverRequest, clientResponse.netSocket(), stopTimer, errorHandler);
                        }));
  }

  private void openTunnel(
      HttpServerRequest serverRequest,
      NetSocket upstream,
      Handler<Void> stopTimer,
      Handler<Throwable> errorHandler) {
    stopTimer.handle(null);
    metrics.httpsTunnelsOpened.inc();
    Tunnels.upgrade(
        serverRequest,
        upstream,
        upPipeFactory,
        downPipeFactory,
        v -> metrics.httpsActiveInc(),
        v -> metrics.httpsActiveDec(),
        errorHandler);
  }
}
