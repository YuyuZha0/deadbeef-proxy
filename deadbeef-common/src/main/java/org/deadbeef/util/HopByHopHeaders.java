package org.deadbeef.util;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.MultiMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.StringUtils;

/**
 * RFC 7230 §6.1 hop-by-hop header handling. These headers describe a single transport-level
 * connection and must not be forwarded end-to-end when relaying a request/response directly to an
 * origin. The set is fixed, plus any field-names listed in the {@code Connection} header of the
 * message being relayed.
 */
public final class HopByHopHeaders {

  private static final Set<String> HOP_BY_HOP =
      Set.of(
          HttpHeaderNames.CONNECTION.toString(),
          HttpHeaderNames.PROXY_CONNECTION.toString(),
          HttpHeaderNames.KEEP_ALIVE.toString(),
          HttpHeaderNames.PROXY_AUTHENTICATE.toString(),
          HttpHeaderNames.PROXY_AUTHORIZATION.toString(),
          HttpHeaderNames.TE.toString(),
          HttpHeaderNames.TRAILER.toString(),
          HttpHeaderNames.TRANSFER_ENCODING.toString(),
          HttpHeaderNames.UPGRADE.toString());

  private HopByHopHeaders() {
    throw new IllegalStateException();
  }

  /** Feed every end-to-end header of {@code headers} to {@code consumer}, dropping hop-by-hop ones. */
  public static void forEachEndToEnd(MultiMap headers, BiConsumer<String, String> consumer) {
    Set<String> connectionTokens = connectionTokens(headers);
    headers.forEach(
        entry -> {
          String name = entry.getKey();
          String lower = name.toLowerCase(Locale.ROOT);
          if (!HOP_BY_HOP.contains(lower) && !connectionTokens.contains(lower)) {
            consumer.accept(name, entry.getValue());
          }
        });
  }

  /** A copy of {@code headers} with hop-by-hop headers removed. */
  public static MultiMap copyEndToEnd(MultiMap headers) {
    MultiMap copy = MultiMap.caseInsensitiveMultiMap();
    forEachEndToEnd(headers, copy::add);
    return copy;
  }

  private static Set<String> connectionTokens(MultiMap headers) {
    String connection = headers.get(HttpHeaderNames.CONNECTION);
    if (StringUtils.isEmpty(connection)) {
      return Set.of();
    }
    Set<String> tokens = new HashSet<>();
    for (String token : StringUtils.split(connection.toLowerCase(Locale.ROOT), ',')) {
      String trimmed = token.trim();
      if (!trimmed.isEmpty()) {
        tokens.add(trimmed);
      }
    }
    return tokens;
  }
}
