package org.deadbeef.route;

import com.google.common.base.Preconditions;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves the upstream target from the request-target (RFC 7230 forward-proxy semantics): a
 * CONNECT request carries an authority-form target, a plain proxied request carries an absolute-form
 * URI. The {@code Host} header is used as a fallback when the request-target is origin-form or
 * absent. {@code defaultPort} applies only to the authority / Host paths (the absolute-URI path
 * derives its default from the scheme).
 */
public final class AuthorityOriginProvider implements OriginProvider {

  private final int defaultPort;

  public AuthorityOriginProvider(int defaultPort) {
    this.defaultPort = defaultPort;
  }

  @Override
  public SocketAddress apply(HttpServerRequest serverRequest) {
    String uri = serverRequest.uri();
    if (serverRequest.method() == HttpMethod.CONNECT) {
      // authority-form request-target, e.g. "example.com:443"
      return Authorities.fromAuthority(firstNonEmpty(uri, hostHeader(serverRequest)), defaultPort);
    }
    if (StringUtils.contains(uri, "://")) {
      // absolute-form request-target (forward-proxy HTTP)
      return Authorities.fromAbsoluteUri(uri);
    }
    // origin-form or missing request-target: fall back to the Host header
    String host = hostHeader(serverRequest);
    Preconditions.checkArgument(
        StringUtils.isNotEmpty(host), "no authority in request-target or Host header");
    return Authorities.fromAuthority(host, defaultPort);
  }

  private static String hostHeader(HttpServerRequest serverRequest) {
    return serverRequest.getHeader(HttpHeaderNames.HOST);
  }

  private static String firstNonEmpty(String a, String b) {
    return StringUtils.isNotEmpty(a) ? a : b;
  }
}
