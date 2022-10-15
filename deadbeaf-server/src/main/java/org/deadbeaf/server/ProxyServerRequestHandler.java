package org.deadbeaf.server;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.util.Utils;

@Slf4j
public final class ProxyServerRequestHandler implements Handler<HttpServerRequest> {

  private final ServerHttpHandler httpHandler;
  private final ServerHttpsHandler httpsHandler;

  public ProxyServerRequestHandler(
      @NonNull ServerHttpHandler httpHandler, @NonNull ServerHttpsHandler httpsHandler) {
    this.httpHandler = httpHandler;
    this.httpsHandler = httpsHandler;
  }

  @Override
  public void handle(HttpServerRequest request) {
    Utils.debugRequest(request, log);
    if (request.method() == HttpMethod.CONNECT) {
      httpsHandler.handle(request);
    } else {
      httpHandler.handle(request);
    }
  }
}
