package org.deadbeef.client;

import com.codahale.metrics.Timer;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.metrics.ProxyMetrics;
import org.deadbeef.route.AddressPicker;
import org.deadbeef.streams.MetricPipeFactory;
import org.deadbeef.streams.PipeFactory;
import org.deadbeef.util.Constants;
import org.deadbeef.util.HttpRequestUtils;
import org.deadbeef.util.Utils;

@Slf4j
public final class ConnectTunnelHandler implements Handler<HttpServerRequest> {

  private final HttpClient httpClient;
  private final AddressPicker addressPicker;
  private final ProxyAuthenticationGenerator generator;
  private final ProxyMetrics metrics;
  private final PipeFactory upPipeFactory;
  private final PipeFactory downPipeFactory;

  public ConnectTunnelHandler(
      @NonNull HttpClient httpClient,
      @NonNull AddressPicker addressPicker,
      @NonNull ProxyAuthenticationGenerator generator,
      @NonNull ProxyMetrics metrics) {
    this.httpClient = httpClient;
    this.addressPicker = addressPicker;
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

    SocketAddress remote = addressPicker.apply(serverRequest);
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
                          stopConnectTimerOnce.handle(null);
                          int status = clientResponse.statusCode();
                          if (status != HttpResponseStatus.OK.code()) {
                            metrics.httpsTunnelsFailed.inc();
                            serverRequest
                                .response()
                                .setStatusCode(status)
                                .end(clientResponse.statusMessage());
                            return;
                          }
                          metrics.httpsTunnelsOpened.inc();
                          NetSocket upstream = clientResponse.netSocket();
                          upgrade(serverRequest, upstream, errorHandler);
                        }));
  }

  private void upgrade(
      HttpServerRequest serverRequest, NetSocket upstream, Handler<Throwable> errorHandler) {
    serverRequest.toNetSocket(
        ar -> {
          if (ar.failed()) {
            errorHandler.handle(ar.cause());
            upstream.close();
            return;
          }
          NetSocket downstream = ar.result();
          metrics.httpsActiveInc();
          // Close-coupling: when either side drops, close the other and decrement the gauge.
          // Subsumes Utils.exchangeCloseHook behaviour while folding in the metric, fires once.
          Handler<Void> closeOnce =
              Utils.atMostOnce(
                  v -> {
                    metrics.httpsActiveDec();
                    downstream.close();
                    upstream.close();
                  });
          downstream.closeHandler(closeOnce);
          upstream.closeHandler(closeOnce);
          upPipeFactory.newPipe(downstream).to(upstream);
          downPipeFactory.newPipe(upstream).to(downstream);
        });
  }
}
