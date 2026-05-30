package org.deadbeef.util;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import io.vertx.core.MultiMap;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * RFC 7230 §6.1 hop-by-hop header handling. These headers describe a single transport-level
 * connection and must not be forwarded end-to-end when relaying a request/response directly to an
 * origin. The set is fixed, plus any field-names listed in the {@code Connection} header of the
 * message being relayed.
 */
public final class HopByHopHeaders {

  @SuppressWarnings(
      "deprecation") // HttpHeaderNames.PROXY_CONNECTION is deprecated but still widely used by
  // buggy clients and proxies
  private static final Set<AsciiString> HOP_BY_HOP =
      ImmutableSet.of(
          HttpHeaderNames.CONNECTION,
          HttpHeaderNames.PROXY_CONNECTION,
          HttpHeaderNames.KEEP_ALIVE,
          HttpHeaderNames.PROXY_AUTHENTICATE,
          HttpHeaderNames.PROXY_AUTHORIZATION,
          HttpHeaderNames.TE,
          HttpHeaderNames.TRAILER,
          HttpHeaderNames.TRANSFER_ENCODING,
          HttpHeaderNames.UPGRADE);

  private HopByHopHeaders() {
    throw new IllegalStateException();
  }

  /**
   * Feed every end-to-end header of {@code headers} to {@code consumer}, dropping hop-by-hop ones.
   */
  public static void forEachEndToEnd(MultiMap headers, BiConsumer<String, String> consumer) {
    Set<AsciiString> connectionTokens = connectionTokens(headers);
    headers.forEach(
        entry -> {
          String name = entry.getKey();
          AsciiString lower = AsciiString.cached(name).toLowerCase();
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

  private static Set<AsciiString> connectionTokens(MultiMap headers) {
    String connection = headers.get(HttpHeaderNames.CONNECTION);
    if (StringUtils.isEmpty(connection)) {
      return ImmutableSet.of();
    }
    return Splitter.on(',')
        .trimResults()
        .omitEmptyStrings()
        .splitToStream(connection)
        .map(AsciiString::cached)
        .map(AsciiString::toLowerCase)
        .collect(Collectors.toSet());
  }
}
