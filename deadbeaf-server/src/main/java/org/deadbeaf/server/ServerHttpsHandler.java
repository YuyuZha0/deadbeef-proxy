package org.deadbeaf.server;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.route.AddressPicker;
import org.deadbeaf.util.Utils;

@Slf4j
public final class ServerHttpsHandler implements Handler<HttpServerRequest> {

  private final AddressPicker addressPicker = AddressPicker.ofHostHeader(443);
  private final NetClient netClient;

  public ServerHttpsHandler(@NonNull NetClient netClient) {
    this.netClient = netClient;
  }

  @Override
  public void handle(HttpServerRequest serverRequest) {
    if (serverRequest.method() != HttpMethod.CONNECT) {
      serverRequest.end();
      return;
    }
    SocketAddress address = addressPicker.apply(serverRequest);
    if (log.isDebugEnabled()) {
      log.info("Receive CONNECT to: {}", address);
    }
    Handler<Throwable> errorHandler = Utils.createErrorHandler(serverRequest.response(), log);
    netClient
        .connect(address)
        .onSuccess(
            upperSocket -> {
              if (log.isDebugEnabled()) {
                log.debug("Open TCP connection on: {}", address);
              }
              serverRequest
                  .toNetSocket()
                  .onSuccess(
                      downSocket -> {
                        Utils.tunnel(upperSocket, downSocket);
                        if (log.isDebugEnabled()) {
                          log.debug(
                              "Create tunnel successfully : {} <=> {}",
                              downSocket.localAddress(),
                              upperSocket.localAddress());
                        }
                      })
                  .onFailure(errorHandler);
            })
        .onFailure(errorHandler);
  }
}
