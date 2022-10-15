package org.deadbeaf.server;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import org.apache.commons.lang3.StringUtils;
import org.deadbeaf.protocol.HttpHeaderEncoder;
import org.deadbeaf.protocol.HttpProto;

import java.util.function.Function;

public final class HttpClientResponseEncoder
    implements Function<HttpClientResponse, HttpProto.Response> {

  private final HttpHeaderEncoder headerEncoder = new HttpHeaderEncoder();

  @Override
  public HttpProto.Response apply(HttpClientResponse clientResponse) {
    HttpProto.Response.Builder builder = HttpProto.Response.newBuilder();
    MultiMap headers = clientResponse.headers();
    if (headers != null) {
      builder.setHeaders(headerEncoder.apply(headers));
    }
    builder.setStatusCode(clientResponse.statusCode());
    String statusMessage = clientResponse.statusMessage();
    if (StringUtils.isNotEmpty(statusMessage)) {
      builder.setStatusMessage(statusMessage);
    }
    return builder.build();
  }
}
