package org.deadbeef.client;

import com.google.common.net.HostAndPort;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.protocol.HttpHeaderEncoder;
import org.deadbeef.protocol.HttpProto;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class HttpServerRequestEncoder
    implements Function<HttpServerRequest, HttpProto.Request> {

  private static final Map<HttpMethod, HttpProto.Method> METHOD_MAP;

  static {
    Map<HttpMethod, HttpProto.Method> map = new HashMap<>();
    for (HttpProto.Method value : HttpProto.Method.values()) {
      map.put(HttpMethod.valueOf(value.name()), value);
    }
    METHOD_MAP = map;
  }

  private final HttpHeaderEncoder headerEncoder = new HttpHeaderEncoder();

  private static HttpProto.Method mapMethod(HttpMethod method) {
    return METHOD_MAP.get(method);
  }

  private static HttpProto.Version mapVersion(HttpVersion version) {
    switch (version) {
      case HTTP_2:
        return HttpProto.Version.HTTP_2;
      case HTTP_1_0:
        return HttpProto.Version.HTTP_1_0;
      default:
        return HttpProto.Version.HTTP_1_1;
    }
  }

  private static String buildAbsoluteUrl(HttpServerRequest request) {
    String hostString = request.getHeader(HttpHeaderNames.HOST);
    HostAndPort hostAndPort = HostAndPort.fromString(hostString);
    StringBuilder builder = new StringBuilder(request.scheme());
    builder.append("://").append(hostAndPort.getHost());
    if (hostAndPort.hasPort()) {
      builder.append(":").append(hostAndPort.getPort());
    }
    String path = request.path();
    if (StringUtils.isNotEmpty(path)
        // Workaround for vert.x currently not support proxy protocol
        && StringUtils.isNotEmpty(path = StringUtils.removeStart(path, hostString))) {
      if (!path.startsWith("/")) {
        builder.append("/");
      }
      builder.append(path);
    }
    String query = request.query();
    if (StringUtils.isNotEmpty(query)) {
      builder.append("?").append(query);
    }
    return builder.toString();
  }

  @Override
  public HttpProto.Request apply(@NonNull HttpServerRequest request) {
    HttpProto.Request.Builder builder = HttpProto.Request.newBuilder();
    builder.setMethod(mapMethod(request.method()));
    builder.setAbsoluteUri(buildAbsoluteUrl(request));
    builder.setScheme(request.scheme());
    builder.setVersion(mapVersion(request.version()));
    builder.setHeaders(headerEncoder.apply(request.headers()));
    return builder.build();
  }
}
