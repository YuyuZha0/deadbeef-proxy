package org.deadbeaf.route;

import com.google.common.base.Preconditions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;

import java.util.function.Function;

public interface AddressPicker extends Function<HttpServerRequest, SocketAddress> {

  static AddressPicker ofStatic(int port, String host) {
    return new StaticAddressPicker(SocketAddress.inetSocketAddress(port, host));
  }

  static AddressPicker fromConfig(JsonObject config, String portKey, String hostKey) {
    Integer port = config.getInteger(portKey);
    Preconditions.checkNotNull(port, "Null port: %s", portKey);
    String host = config.getString(hostKey);
    Preconditions.checkNotNull(host, "Null host: %s", hostKey);
    return ofStatic(port, host);
  }

  static AddressPicker ofHostHeader(int defaultPort) {
    return new HostHeaderAddressPicker(defaultPort);
  }
}
