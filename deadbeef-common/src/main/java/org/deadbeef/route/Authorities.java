package org.deadbeef.route;

import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.lang3.StringUtils;

/**
 * Parsing helpers for resolving an upstream target from an HTTP request-target, returning Vert.x
 * {@link SocketAddress} for consistency with the rest of the routing code. Shared by the
 * forward-proxy server (which routes on a decoded absolute-URI / CONNECT authority) and the client
 * (which resolves the direct target the same way), so the rules live in one place. Guava's {@code
 * HostAndPort} is used only internally as the {@code host:port} parser.
 */
public final class Authorities {

  private Authorities() {
    throw new IllegalStateException();
  }

  /**
   * Parse an authority-form target such as {@code "example.com:443"} or {@code "example.com"},
   * applying {@code defaultPort} when no port is present (e.g. the CONNECT request-line authority).
   */
  public static SocketAddress fromAuthority(String authority, int defaultPort) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(authority), "empty authority");
    HostAndPort hostAndPort = HostAndPort.fromString(authority).withDefaultPort(defaultPort);
    return SocketAddress.inetSocketAddress(hostAndPort.getPort(), hostAndPort.getHost());
  }

  /**
   * Parse an absolute-form request URI such as {@code "http://example.com/path"}, defaulting the
   * port from the scheme (443 for https, 80 otherwise) when the URI carries no explicit port.
   */
  public static SocketAddress fromAbsoluteUri(String absoluteUri) {
    URI uri;
    try {
      uri = new URI(absoluteUri);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("invalid absolute URI: " + absoluteUri, e);
    }
    String host = uri.getHost();
    Preconditions.checkArgument(StringUtils.isNotEmpty(host), "no host in URI: %s", absoluteUri);
    int port =
        uri.getPort() != -1
            ? uri.getPort()
            : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    return SocketAddress.inetSocketAddress(port, host);
  }
}
