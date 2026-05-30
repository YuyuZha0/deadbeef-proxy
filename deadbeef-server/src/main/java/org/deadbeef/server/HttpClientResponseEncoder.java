package org.deadbeef.server;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.protocol.HttpProto;
import org.deadbeef.util.HopByHopHeaders;
import org.deadbeef.util.HttpHeaderEncoder;

public final class HttpClientResponseEncoder
    implements Function<HttpClientResponse, HttpProto.Response> {

  private final HttpHeaderEncoder headerEncoder = new HttpHeaderEncoder();

  @Override
  public HttpProto.Response apply(HttpClientResponse clientResponse) {
    HttpProto.Response.Builder builder = HttpProto.Response.newBuilder();
    MultiMap headers = clientResponse.headers();
    if (headers != null) {
      // Strip hop-by-hop headers so the origin's connection-scoped headers (Connection,
      // Transfer-Encoding, Keep-Alive, ...) are not replayed verbatim to the browser; the client
      // re-frames the response on its own leg.
      builder.setHeaders(headerEncoder.apply(HopByHopHeaders.copyEndToEnd(headers)));
    }
    builder.setStatusCode(clientResponse.statusCode());
    String statusMessage = clientResponse.statusMessage();
    if (StringUtils.isNotEmpty(statusMessage)) {
      builder.setStatusMessage(statusMessage);
    }
    return builder.build();
  }
}
