package org.deadbeaf.client;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.util.Utils;

@Slf4j
public final class ProxyClientRequestHandler implements Handler<HttpServerRequest> {

  private final ClientHttpHandler httpHandler;
  private final ClientHttpsHandler httpsHandler;

  public ProxyClientRequestHandler(
      @NonNull ClientHttpHandler httpHandler, @NonNull ClientHttpsHandler httpsHandler) {
    this.httpHandler = httpHandler;
    this.httpsHandler = httpsHandler;
  }

  @Override
  public void handle(HttpServerRequest request) {
    Utils.debugRequest(request, log);
    request.headers().remove(HttpHeaderNames.PROXY_AUTHORIZATION);
    if (request.method() == HttpMethod.CONNECT) {
      httpsHandler.handle(request);
    } else {
      httpHandler.handle(request);
    }
  }
}
