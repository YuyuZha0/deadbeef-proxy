package org.deadbeef.route;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import java.util.function.Function;

public sealed interface OriginProvider extends Function<HttpServerRequest, SocketAddress>
    permits AuthorityOriginProvider, StaticOriginProvider {

  static OriginProvider ofStatic(int port, String host) {
    return new StaticOriginProvider(SocketAddress.inetSocketAddress(port, host));
  }

  static OriginProvider ofAuthority(int defaultPort) {
    return new AuthorityOriginProvider(defaultPort);
  }

  @Override
  SocketAddress apply(HttpServerRequest serverRequest);
}
