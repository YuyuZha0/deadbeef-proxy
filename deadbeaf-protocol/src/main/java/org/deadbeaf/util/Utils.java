package org.deadbeaf.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.TCPSSLOptions;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Utils {

  private static final ObjectMapper YAML_MAPPER =
      new YAMLMapper().registerModule(new VertxJsonModule());

  private Utils() {
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

  public static Handler<Throwable> createErrorHandler(
      @NonNull HttpServerResponse response, @NonNull Logger logger) {
    AtomicBoolean executed = new AtomicBoolean(false);
    return (Throwable cause) -> {
      if (response.closed() || response.ended()) {
        logger.warn("The response already ended, omitted exception: ", cause);
        return;
      }
      if (!executed.compareAndSet(false, true)) {
        logger.warn("Error handler called multiple times, exception: ", cause);
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
    };
  }

  public static void handleClosing(
      @NonNull HttpServerRequest downStreamRequest, @NonNull HttpClientRequest upperStreamRequest) {
    upperStreamRequest
        .connection()
        .closeHandler(
            v -> {
              HttpServerResponse response = downStreamRequest.response();
              if (response.closed() || response.ended()) {
                return;
              }
              response.setStatusCode(HttpResponseStatus.BAD_GATEWAY.code()).end();
            });
  }

  public static void tunnel(NetSocket alice, NetSocket bob) {
    new SocketTunnel(alice, bob).open();
  }

  public static String lineSeparator() {
    return System.lineSeparator();
  }

  public static String rightArrow() {
    return "[-->]";
  }

  public static String leftArrow() {
    return "[<--]";
  }

  public static void debugRequest(HttpServerRequest request, Logger logger) {
    if (logger.isDebugEnabled()) {
      String msg =
          Strings.lenientFormat(
              "%s => %s, %s, %s, %s",
              request.remoteAddress(),
              request.method(),
              request.version(),
              request.getHeader(HttpHeaderNames.HOST),
              request.path());
      logger.debug(msg);
    }
  }

  public static ObjectMapper yamlMapper() {
    return YAML_MAPPER;
  }

  public static JsonObject loadConfig(String pathStr) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(pathStr), "empty config path!");
    Path path = Paths.get(pathStr);
    Preconditions.checkArgument(
        Files.exists(path) && Files.isRegularFile(path) && Files.isReadable(path),
        "Illegal config path: %s",
        pathStr);
    try (InputStream inputStream = Files.newInputStream(path)) {
      return yamlMapper().readValue(inputStream, JsonObject.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T extends TCPSSLOptions> T enableTcpOptimizationWhenAvailable(
      Vertx vertx, T options) {
    if (vertx.isNativeTransportEnabled()) {
      options.setTcpFastOpen(true);
      options.setTcpNoDelay(true);
      options.setTcpQuickAck(true);
    }
    return options;
  }
}
