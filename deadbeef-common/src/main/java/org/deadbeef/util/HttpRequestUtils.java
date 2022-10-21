package org.deadbeef.util;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.NoStackTraceThrowable;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.concurrent.TimeoutException;

public final class HttpRequestUtils {

  private HttpRequestUtils() {
    throw new IllegalStateException();
  }

  public static long contentLength(MultiMap headers) {
    if (headers == null || headers.isEmpty()) {
      return 0L;
    }
    String transferEncoding = headers.get(HttpHeaderNames.TRANSFER_ENCODING);
    if (StringUtils.isNotEmpty(transferEncoding)) {
      return -1L; // stream, not fixed
    }
    String contentLength = headers.get(HttpHeaderNames.CONTENT_LENGTH);
    if (StringUtils.isNotEmpty(contentLength)) {
      try {
        return Long.parseLong(contentLength);
      } catch (NumberFormatException ignore) {
      }
    }
    return 0L;
  }

  public static void debugRequest(HttpServerRequest request, Logger logger) {
    if (logger.isDebugEnabled()) {
      String msg =
          Strings.lenientFormat(
              "%s => %s, %s, %s, %s",
              request.remoteAddress(),
              request.method(),
              request.version(),
              MoreObjects.firstNonNull(request.getHeader(HttpHeaderNames.HOST), request.host()),
              request.path());
      logger.debug(msg);
    }
  }

  public static Handler<Throwable> createErrorHandler(
      @NonNull HttpServerResponse response, @NonNull Logger logger) {
    return Utils.atMostOnce(
        (Throwable cause) -> {
          if (response.closed() || response.ended()) {
            logger.warn("The response already ended, omitted exception: ", cause);
            return;
          }
          Buffer msg;
          if (cause instanceof NoStackTraceThrowable) {
            msg = Buffer.buffer(cause.getMessage(), "UTF-8");
          } else {
            msg = Buffer.buffer(Throwables.getStackTraceAsString(cause), "UTF-8");
          }
          response
              .setStatusCode(
                  (cause instanceof TimeoutException)
                      ? HttpResponseStatus.GATEWAY_TIMEOUT.code()
                      : HttpResponseStatus.BAD_GATEWAY.code())
              .putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
              .putHeader(HttpHeaderNames.CONTENT_LENGTH, Integer.toString(msg.length()))
              .end(msg);
        });
  }
}
