package org.deadbeef.server;

import com.google.common.net.HostAndPort;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.auth.ProxyAuthenticationValidator;
import org.deadbeef.security.UpstreamAddressFilter;
import org.deadbeef.security.UpstreamResolver;
import org.deadbeef.streams.PipeFactory;
import org.deadbeef.util.Constants;
import org.deadbeef.util.Utils;

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

    HostAndPort target;
    try {
      target = HostAndPort.fromString(authority).withDefaultPort(443);
    } catch (IllegalArgumentException e) {
      response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
      return;
    }

    request.pause();

    UpstreamResolver.resolveAndFilter(vertx, target.getHost(), target.getPort(), addressFilter)
        .onFailure(cause -> UpstreamResolver.replyWithError(target.getHost(), cause, response))
        .onSuccess(
            socketAddress ->
                netClient
                    .connect(socketAddress)
                    .onFailure(
                        cause -> UpstreamResolver.replyWithError(target.getHost(), cause, response))
                    .onSuccess(upstream -> upgrade(request, upstream)));
  }

  private void upgrade(HttpServerRequest request, NetSocket upstream) {
    request.toNetSocket(
        ar -> {
          if (ar.failed()) {
            log.warn("CONNECT upgrade failed: ", ar.cause());
            upstream.close();
            return;
          }
          NetSocket downstream = ar.result();
          Utils.exchangeCloseHook(upstream, downstream);
          pipeFactory.newPipe(upstream).to(downstream);
          pipeFactory.newPipe(downstream).to(upstream);
        });
  }
}
