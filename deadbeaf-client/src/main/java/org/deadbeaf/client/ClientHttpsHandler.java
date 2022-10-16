package org.deadbeaf.client;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deadbeaf.auth.ProxyAuthenticationGenerator;
import org.deadbeaf.route.AddressPicker;
import org.deadbeaf.util.Constants;
import org.deadbeaf.util.Utils;

@Slf4j
public final class ClientHttpsHandler implements Handler<HttpServerRequest> {

  private final HttpClient httpClient;
  private final AddressPicker addressPicker;

  private final ProxyAuthenticationGenerator proxyAuthenticationGenerator;

  public ClientHttpsHandler(
      @NonNull HttpClient httpClient,
      @NonNull AddressPicker addressPicker,
      @NonNull ProxyAuthenticationGenerator generator) {
    this.httpClient = httpClient;
    this.addressPicker = addressPicker;
    this.proxyAuthenticationGenerator = generator;
  }

  @Override
  public void handle(HttpServerRequest serverRequest) {
    if (serverRequest.method() != HttpMethod.CONNECT
        || StringUtils.isEmpty(serverRequest.getHeader(HttpHeaderNames.HOST))) {
      serverRequest.response().setStatusCode(HttpResponseStatus.FORBIDDEN.code()).end();
      return;
    }
    RequestOptions requestOptions = new RequestOptions();
    requestOptions.setMethod(HttpMethod.CONNECT);
    SocketAddress address = addressPicker.apply(serverRequest);
    requestOptions.setServer(address);
    serverRequest.headers().forEach(requestOptions::putHeader);
    requestOptions.putHeader(Constants.authHeaderName(), proxyAuthenticationGenerator.get());
    requestOptions.setTimeout(Constants.requestTimeout());

    HttpServerResponse serverResponse = serverRequest.response();
    Handler<Throwable> errorHandler = Utils.createErrorHandler(serverResponse, log);
    httpClient
        .request(requestOptions)
        .onSuccess(
            clientRequest -> {
              if (log.isDebugEnabled()) {
                log.debug("Send [CONNECT] successfully to: {}", address);
              }
              clientRequest
                  .connect()
                  .onSuccess(
                      clientResponse -> {
                        serverResponse.setStatusCode(clientResponse.statusCode());
                        serverResponse.setStatusMessage(clientResponse.statusMessage());
                        clientResponse.headers().forEach(serverResponse::putHeader);
                        if (clientResponse.statusCode() == HttpResponseStatus.OK.code()) {
                          serverRequest
                              .toNetSocket()
                              .onSuccess(
                                  downSocket ->
                                      Utils.tunnel(downSocket, clientResponse.netSocket()))
                              .onFailure(errorHandler);
                        } else {
                          serverResponse.end();
                        }
                      })
                  .onFailure(errorHandler);
            })
        .onFailure(errorHandler);
  }
}
