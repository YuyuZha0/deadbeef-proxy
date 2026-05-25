package org.deadbeef.security;

import com.google.common.net.InetAddresses;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.util.HttpRequestUtils;

import java.net.InetAddress;

/**
 * Shared "resolve a hostname via Vert.x's configured resolver, then apply the {@link
 * UpstreamAddressFilter}" pipeline. Used by both server-side handlers (HTTP-proxy and CONNECT) so
 * SSRF policy is enforced uniformly.
 *
 * <p>Literal IP inputs bypass DNS and are parsed via Guava. Hostname inputs go through {@link
 * io.vertx.core.impl.AddressResolver}, which honours the {@code addressResolver:} list in {@code
 * server-config.yaml}. Using {@code InetAddress.getByName} here would silently bypass that config.
 *
 * <p>The returned address is a Vert.x {@link SocketAddress} pinned to the resolved IP literal, so
 * the caller's subsequent {@code netClient.connect(...)} / {@code requestOptions.setServer(...)}
 * call does not trigger another (potentially rebound) DNS lookup.
 */
@Slf4j
public final class UpstreamResolver {

  private UpstreamResolver() {
    throw new IllegalStateException();
  }

  /**
   * Resolves {@code host} and applies {@code filter}, returning a Vert.x {@link SocketAddress}
   * bound to the resolved IP + given {@code port}.
   *
   * <p>Failure modes on the returned future:
   *
   * <ul>
   *   <li>{@link UpstreamRejectedException} — filter denied the resolved address.
   *   <li>Anything else (e.g. {@code UnknownHostException}) — DNS / resolver error.
   * </ul>
   */
  public static Future<SocketAddress> resolveAndFilter(
      @NonNull Vertx vertx,
      @NonNull String host,
      int port,
      @NonNull UpstreamAddressFilter filter) {
    return resolve(vertx, host)
        .compose(
            addr -> {
              String reason = filter.denyReason(addr);
              if (reason != null) {
                return Future.failedFuture(new UpstreamRejectedException(host, addr, reason));
              }
              return Future.succeededFuture(
                  SocketAddress.inetSocketAddress(port, addr.getHostAddress()));
            });
  }

  /**
   * Maps a failure from {@link #resolveAndFilter} to an HTTP status on the supplied response:
   * {@code 403} for a filter rejection, otherwise {@link HttpRequestUtils#errorMapping} (timeout →
   * 504, anything else → 502).
   */
  public static void replyWithError(
      @NonNull String host, @NonNull Throwable cause, @NonNull HttpServerResponse response) {
    if (cause instanceof UpstreamRejectedException rej) {
      log.info(
          "Rejecting upstream {} ({}): {}", host, rej.address().getHostAddress(), rej.reason());
      response.setStatusCode(403).end();
      return;
    }
    log.warn("Resolve failure for upstream {}: {}", host, cause.getMessage());
    response.setStatusCode(HttpRequestUtils.errorMapping(cause).code()).end();
  }

  // ---- internals ----

  private static Future<InetAddress> resolve(Vertx vertx, String host) {
    if (InetAddresses.isInetAddress(host)) {
      return Future.succeededFuture(InetAddresses.forString(host));
    }
    Promise<InetAddress> promise = Promise.promise();
    ((VertxInternal) vertx).addressResolver().resolveHostname(host, promise);
    return promise.future();
  }
}
