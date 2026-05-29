package org.deadbeef.client;

import com.codahale.metrics.MetricRegistry;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import java.io.ByteArrayOutputStream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.metrics.ProxyMetrics;

/**
 * Tiny admin HTTP server that exposes a live metrics dashboard. Bound to {@code 127.0.0.1} so the
 * dashboard never leaks onto the LAN; no authentication, no proxy auth header, no TLS — it's a
 * loopback-only operator surface.
 *
 * <ul>
 *   <li>{@code GET /} → the static {@code dashboard.html} page from the classpath.
 *   <li>{@code GET /api/metrics} → a {@link ProxyMetrics#toJson} JSON dump.
 *   <li>anything else → 404.
 * </ul>
 */
@Slf4j
public final class MetricsDashboardServer {

  private static final String DASHBOARD_PATH = "/";
  private static final String METRICS_PATH = "/api/metrics";
  private static final String BIND_HOST = "127.0.0.1";
  private static final String DASHBOARD_RESOURCE = "dashboard.html";

  private final Vertx vertx;
  private final MetricRegistry registry;
  private final int port;
  private volatile HttpServer server;
  private volatile Buffer cachedDashboard;

  public MetricsDashboardServer(@NonNull Vertx vertx, @NonNull MetricRegistry registry, int port) {
    this.vertx = vertx;
    this.registry = registry;
    this.port = port;
  }

  private static void writeDashboard(HttpServerRequest request, Buffer body) {
    request
        .response()
        .putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML + "; charset=utf-8")
        .end(body);
  }

  public Future<HttpServer> start() {
    HttpServer http = vertx.createHttpServer();
    http.requestHandler(this::route);
    return http.listen(port, BIND_HOST)
        .onSuccess(
            srv -> {
              this.server = srv;
              log.info("Metrics dashboard listening on http://{}:{}/", BIND_HOST, srv.actualPort());
            })
        .onFailure(
            cause ->
                log.error(
                    "Failed to start metrics dashboard on {}:{}: {}",
                    BIND_HOST,
                    port,
                    cause.getMessage()));
  }

  public Future<Void> close() {
    HttpServer s = server;
    return s == null ? Future.succeededFuture() : s.close();
  }

  private void route(HttpServerRequest request) {
    String path = request.path();
    if (DASHBOARD_PATH.equals(path)) {
      serveDashboard(request);
    } else if (METRICS_PATH.equals(path)) {
      serveMetrics(request);
    } else {
      request.response().setStatusCode(404).end();
    }
  }

  private void serveDashboard(HttpServerRequest request) {
    Buffer cached = cachedDashboard;
    if (cached != null) {
      writeDashboard(request, cached);
      return;
    }
    // Read once, cache forever. The file is shipped on the classpath and never changes at runtime.
    vertx
        .executeBlocking(this::loadDashboardResource)
        .onSuccess(
            body -> {
              cachedDashboard = body;
              writeDashboard(request, body);
            })
        .onFailure(
            cause -> {
              log.error("Failed to load {}: {}", DASHBOARD_RESOURCE, cause.getMessage());
              request.response().setStatusCode(500).end("dashboard.html missing");
            });
  }

  private Buffer loadDashboardResource() throws Exception {
    try (java.io.InputStream in =
        getClass().getClassLoader().getResourceAsStream(DASHBOARD_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("classpath resource missing: " + DASHBOARD_RESOURCE);
      }
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        in.transferTo(out);
        return Buffer.buffer(out.toByteArray());
      }
    }
  }

  private void serveMetrics(HttpServerRequest request) {
    request
        .response()
        .putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        .putHeader(HttpHeaderNames.CACHE_CONTROL, "no-store")
        .end(ProxyMetrics.toJson(registry).encode());
  }
}
