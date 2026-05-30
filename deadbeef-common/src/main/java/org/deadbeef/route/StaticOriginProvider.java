package org.deadbeef.route;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;

public final class StaticOriginProvider implements OriginProvider {
  private final SocketAddress socketAddress;

  public StaticOriginProvider(@NonNull SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
  }

  @Override
  public SocketAddress apply(HttpServerRequest serverRequest) {
    return socketAddress;
  }
}
