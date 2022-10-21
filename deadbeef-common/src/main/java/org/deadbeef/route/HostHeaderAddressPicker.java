package org.deadbeef.route;

import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import org.apache.commons.lang3.StringUtils;

public final class HostHeaderAddressPicker implements AddressPicker {

  private final int defaultPort;

  public HostHeaderAddressPicker(int defaultPort) {
    this.defaultPort = defaultPort;
  }

  @Override
  public SocketAddress apply(HttpServerRequest serverRequest) {
    String hostHeader = serverRequest.getHeader(HttpHeaderNames.HOST);
    Preconditions.checkArgument(StringUtils.isNotEmpty(hostHeader), "null HOST!");
    HostAndPort hostAndPort = HostAndPort.fromString(hostHeader).withDefaultPort(defaultPort);
    return SocketAddress.inetSocketAddress(hostAndPort.getPort(), hostAndPort.getHost());
  }
}
