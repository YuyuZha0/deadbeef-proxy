package org.deadbeef.client;

import com.codahale.metrics.MetricRegistry;
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
import org.deadbeef.route.AddressPicker;
import org.deadbeef.streams.MetricPipeFactory;
import org.deadbeef.streams.PipeFactory;
import org.deadbeef.streams.StreamType;
import org.deadbeef.util.Constants;
import org.deadbeef.util.HttpRequestUtils;
import org.deadbeef.util.Utils;

@Slf4j
public final class ConnectTunnelHandler implements Handler<HttpServerRequest> {

  private final HttpClient httpClient;
  private final AddressPicker addressPicker;
  private final ProxyAuthenticationGenerator generator;
  private final PipeFactory upPipeFactory;
  private final PipeFactory downPipeFactory;

  public ConnectTunnelHandler(
      @NonNull HttpClient httpClient,
      @NonNull AddressPicker addressPicker,
      @NonNull ProxyAuthenticationGenerator generator,
      @NonNull MetricRegistry metricRegistry) {
    this.httpClient = httpClient;
    this.addressPicker = addressPicker;
    this.generator = generator;
    this.upPipeFactory = new MetricPipeFactory(metricRegistry, StreamType.HTTPS_UP);
    this.downPipeFactory = new MetricPipeFactory(metricRegistry, StreamType.HTTPS_DOWN);
  }

  @Override
  public void handle(HttpServerRequest serverRequest) {
    if (serverRequest.method() != HttpMethod.CONNECT
        || StringUtils.isEmpty(serverRequest.getHeader(HttpHeaderNames.HOST))) {
      serverRequest.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
      return;
    }

    SocketAddress remote = addressPicker.apply(serverRequest);
    Handler<Throwable> errorHandler = HttpRequestUtils.createErrorHandler(serverRequest.response());
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
                          int status = clientResponse.statusCode();
                          if (status != HttpResponseStatus.OK.code()) {
                            serverRequest
                                .response()
                                .setStatusCode(status)
                                .end(clientResponse.statusMessage());
                            return;
                          }
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
          Utils.exchangeCloseHook(downstream, upstream);
          upPipeFactory.newPipe(downstream).to(upstream);
          downPipeFactory.newPipe(upstream).to(downstream);
        });
  }
}
