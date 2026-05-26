package org.deadbeef.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import lombok.NonNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central registry of all proxy metrics under the {@code proxy.*} namespace. One instance per
 * {@link MetricRegistry}; all metrics are registered eagerly so the dashboard sees zero-valued
 * series from t=0 instead of "metric not yet seen" gaps.
 *
 * <p>Naming convention:
 *
 * <ul>
 *   <li>{@code proxy.http.*} — plaintext-HTTP-proxy flow (browser POST → server → upstream HTTP)
 *   <li>{@code proxy.https.*} — HTTPS tunnel flow (browser CONNECT → server → upstream TCP)
 *   <li>{@code requests.*} — count/state of HTTP requests
 *   <li>{@code tunnels.*} — count/state of HTTPS tunnels
 *   <li>{@code responses.[2-5]xx} — status-code distribution
 *   <li>{@code bytes.up} / {@code bytes.down} — wire throughput, browser→upstream / upstream→browser
 *   <li>{@code *.duration} — full-cycle timer
 * </ul>
 */
public final class ProxyMetrics {

  // ---- HTTP-proxy flow ----
  public final Counter httpRequestsTotal;
  public final Counter httpRequestsFailed;
  public final Counter httpResponse2xx;
  public final Counter httpResponse3xx;
  public final Counter httpResponse4xx;
  public final Counter httpResponse5xx;
  public final Timer httpRequestDuration;
  public final Meter httpBytesUp;
  public final Meter httpBytesDown;
  private final AtomicInteger httpInFlight = new AtomicInteger();

  // ---- HTTPS-tunnel flow ----
  public final Counter httpsTunnelsOpened;
  public final Counter httpsTunnelsFailed;
  public final Timer httpsConnectDuration;
  public final Meter httpsBytesUp;
  public final Meter httpsBytesDown;
  private final AtomicInteger httpsActive = new AtomicInteger();

  public ProxyMetrics(@NonNull MetricRegistry registry) {
    this.httpRequestsTotal = registry.counter("proxy.http.requests.total");
    this.httpRequestsFailed = registry.counter("proxy.http.requests.failed");
    this.httpResponse2xx = registry.counter("proxy.http.responses.2xx");
    this.httpResponse3xx = registry.counter("proxy.http.responses.3xx");
    this.httpResponse4xx = registry.counter("proxy.http.responses.4xx");
    this.httpResponse5xx = registry.counter("proxy.http.responses.5xx");
    this.httpRequestDuration = registry.timer("proxy.http.request.duration");
    this.httpBytesUp = registry.meter("proxy.http.bytes.up");
    this.httpBytesDown = registry.meter("proxy.http.bytes.down");
    registry.register(
        "proxy.http.requests.in_flight", (Gauge<Integer>) httpInFlight::get);

    this.httpsTunnelsOpened = registry.counter("proxy.https.tunnels.opened");
    this.httpsTunnelsFailed = registry.counter("proxy.https.tunnels.failed");
    this.httpsConnectDuration = registry.timer("proxy.https.connect.duration");
    this.httpsBytesUp = registry.meter("proxy.https.bytes.up");
    this.httpsBytesDown = registry.meter("proxy.https.bytes.down");
    registry.register("proxy.https.tunnels.active", (Gauge<Integer>) httpsActive::get);
  }

  // ---- gauge helpers (in-flight tracking) ----

  public void httpInFlightInc() {
    httpInFlight.incrementAndGet();
  }

  public void httpInFlightDec() {
    httpInFlight.decrementAndGet();
  }

  public int httpInFlight() {
    return httpInFlight.get();
  }

  public void httpsActiveInc() {
    httpsActive.incrementAndGet();
  }

  public void httpsActiveDec() {
    httpsActive.decrementAndGet();
  }

  public int httpsActive() {
    return httpsActive.get();
  }

  /** Bucket an HTTP status code into the appropriate counter. */
  public void recordHttpStatus(int statusCode) {
    if (statusCode >= 200 && statusCode < 300) {
      httpResponse2xx.inc();
    } else if (statusCode >= 300 && statusCode < 400) {
      httpResponse3xx.inc();
    } else if (statusCode >= 400 && statusCode < 500) {
      httpResponse4xx.inc();
    } else if (statusCode >= 500 && statusCode < 600) {
      httpResponse5xx.inc();
    }
  }
}
