package org.deadbeef.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.auth.ProxyAuthenticationValidator;
import org.deadbeef.route.Authorities;
import org.deadbeef.security.UpstreamAddressFilter;
import org.deadbeef.security.UpstreamResolver;
import org.deadbeef.streams.PipeFactory;
import org.deadbeef.streams.Tunnels;
import org.deadbeef.util.Constants;

@Slf4j
public final class ServerConnectHandler implements Handler<HttpServerRequest> {

  private final Vertx vertx;
  private final NetClient netClient;
  private final ProxyAuthenticationValidator validator;
  private final PipeFactory pipeFactory;
  private final UpstreamAddressFilter addressFilter;

  public ServerConnectHandler(
      @NonNull Vertx vertx,
      @NonNull NetClient netClient,
      @NonNull ProxyAuthenticationValidator validator,
      @NonNull PipeFactory pipeFactory,
      @NonNull UpstreamAddressFilter addressFilter) {
    this.vertx = vertx;
    this.netClient = netClient;
    this.validator = validator;
    this.pipeFactory = pipeFactory;
    this.addressFilter = addressFilter;
  }

  @Override
  public void handle(HttpServerRequest request) {
    HttpServerResponse response = request.response();

    if (!validator.testString(request.getHeader(Constants.authHeaderName()))) {
      response.setStatusCode(HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED.code()).end();
      return;
    }

    String authority = request.uri();
    if (StringUtils.isEmpty(authority)) {
      response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
      return;
    }

    SocketAddress target;
    try {
      target = Authorities.fromAuthority(authority, 443);
    } catch (IllegalArgumentException e) {
      response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
      return;
    }

    request.pause();

    UpstreamResolver.resolveAndFilter(vertx, target.host(), target.port(), addressFilter)
        .onFailure(cause -> UpstreamResolver.replyWithError(target.host(), cause, response))
        .onSuccess(
            socketAddress ->
                netClient
                    .connect(socketAddress)
                    .onFailure(
                        cause -> UpstreamResolver.replyWithError(target.host(), cause, response))
                    .onSuccess(upstream -> upgrade(request, upstream)));
  }

  private void upgrade(HttpServerRequest request, NetSocket upstream) {
    Tunnels.upgrade(
        request,
        upstream,
        pipeFactory,
        pipeFactory,
        null,
        null,
        cause -> log.warn("CONNECT upgrade failed: ", cause));
  }
}
