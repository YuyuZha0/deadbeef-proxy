package org.deadbeef.client;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.util.HttpRequestUtils;

@Slf4j
public final class ProxyClientRequestHandler implements Handler<HttpServerRequest> {

  private final HttpProxyHandler httpHandler;
  private final ConnectTunnelHandler httpsHandler;

  public ProxyClientRequestHandler(
      @NonNull HttpProxyHandler httpHandler, @NonNull ConnectTunnelHandler httpsHandler) {
    this.httpHandler = httpHandler;
    this.httpsHandler = httpsHandler;
  }

  @Override
  public void handle(HttpServerRequest request) {
    HttpRequestUtils.debugRequest(request, log);
    if (request.method() == HttpMethod.CONNECT) {
      httpsHandler.handle(request);
    } else {
      httpHandler.handle(request);
    }
  }
}
