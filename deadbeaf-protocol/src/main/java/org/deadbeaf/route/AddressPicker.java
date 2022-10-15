package org.deadbeaf.route;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;

import java.util.function.Function;

public interface AddressPicker extends Function<HttpServerRequest, SocketAddress> {

  static AddressPicker ofStatic(int port, String host) {
    return new StaticAddressPicker(SocketAddress.inetSocketAddress(port, host));
  }

  static AddressPicker ofHostHeader(int defaultPort) {
    return new HostHeaderAddressPicker(defaultPort);
  }
}
