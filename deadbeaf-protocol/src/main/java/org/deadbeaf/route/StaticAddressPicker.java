package org.deadbeaf.route;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;

public final class StaticAddressPicker implements AddressPicker {
  private final SocketAddress socketAddress;

  public StaticAddressPicker(@NonNull SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
  }

  @Override
  public SocketAddress apply(HttpServerRequest serverRequest) {
    return socketAddress;
  }
}
