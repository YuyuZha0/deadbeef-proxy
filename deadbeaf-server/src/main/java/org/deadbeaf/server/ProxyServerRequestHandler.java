package org.deadbeaf.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.auth.ProxyAuthenticationValidator;
import org.deadbeaf.util.Constants;
import org.deadbeaf.util.Utils;

@Slf4j
public final class ProxyServerRequestHandler implements Handler<HttpServerRequest> {

  private final ServerHttpHandler httpHandler;
  private final ServerHttpsHandler httpsHandler;

  private final ProxyAuthenticationValidator validator;

  public ProxyServerRequestHandler(
      @NonNull ServerHttpHandler httpHandler,
      @NonNull ServerHttpsHandler httpsHandler,
      @NonNull ProxyAuthenticationValidator validator) {
    this.httpHandler = httpHandler;
    this.httpsHandler = httpsHandler;
    this.validator = validator;
  }

  @Override
  public void handle(HttpServerRequest request) {
    Utils.debugRequest(request, log);
    if (!validator.test(request.getHeader(Constants.authHeaderName()))) {
      request
          .response()
          .setStatusCode(HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED.code())
          .end();
      return;
    }
    if (request.method() == HttpMethod.CONNECT) {
      request.headers().remove(Constants.authHeaderName());
      httpsHandler.handle(request);
    } else {
      httpHandler.handle(request);
    }
  }
}
