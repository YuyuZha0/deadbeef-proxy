package org.deadbeef.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.NonNull;

/**
 * Central registry of all proxy metrics under the {@code proxy.*} namespace. One instance per
 * {@link MetricRegistry}; all metrics are registered eagerly so the dashboard sees zero-valued
 * series from t=0 instead of "metric not yet seen" gaps. Also owns {@link #toJson} — the snapshot
 * serialiser the dashboard's polling endpoint emits.
 *
 * <p>Naming convention:
 *
 * <ul>
 *   <li>{@code proxy.http.*} — plaintext-HTTP-proxy flow (browser POST → server → upstream HTTP)
 *   <li>{@code proxy.https.*} — HTTPS tunnel flow (browser CONNECT → server → upstream TCP)
 *   <li>{@code requests.*} — count/state of HTTP requests
 *   <li>{@code tunnels.*} — count/state of HTTPS tunnels
 *   <li>{@code *.direct} / {@code *.remote} — served directly vs. via the remote proxy
 *   <li>{@code responses.[2-5]xx} — status-code distribution
 *   <li>{@code bytes.up} / {@code bytes.down} — wire throughput, browser→upstream / upstream→browser
 *   <li>{@code *.duration} — full-cycle timer
 * </ul>
 */
public final class ProxyMetrics {

  // ---- HTTP-proxy flow ----
  public final Counter httpRequestsTotal;
  public final Counter httpRequestsFailed;
  public final Counter httpRequestsDirect;
  public final Counter httpRequestsRemote;
  public final Counter httpResponse2xx;
  public final Counter httpResponse3xx;
  public final Counter httpResponse4xx;
  public final Counter httpResponse5xx;
  public final Timer httpRequestDuration;
  public final Meter httpBytesUp;
  public final Meter httpBytesDown;
  // ---- HTTPS-tunnel flow ----
  public final Counter httpsTunnelsOpened;
  public final Counter httpsTunnelsFailed;
  public final Counter httpsDirectTunnels;
  public final Counter httpsRemoteTunnels;
  public final Timer httpsConnectDuration;
  public final Meter httpsBytesUp;
  public final Meter httpsBytesDown;
  private final AtomicInteger httpInFlight = new AtomicInteger();
  private final AtomicInteger httpsActive = new AtomicInteger();

  public ProxyMetrics(@NonNull MetricRegistry registry) {
    this.httpRequestsTotal = registry.counter("proxy.http.requests.total");
    this.httpRequestsFailed = registry.counter("proxy.http.requests.failed");
    this.httpRequestsDirect = registry.counter("proxy.http.requests.direct");
    this.httpRequestsRemote = registry.counter("proxy.http.requests.remote");
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
    this.httpsDirectTunnels = registry.counter("proxy.https.tunnels.direct");
    this.httpsRemoteTunnels = registry.counter("proxy.https.tunnels.remote");
    this.httpsConnectDuration = registry.timer("proxy.https.connect.duration");
    this.httpsBytesUp = registry.meter("proxy.https.bytes.up");
    this.httpsBytesDown = registry.meter("proxy.https.bytes.down");
    registry.register("proxy.https.tunnels.active", (Gauge<Integer>) httpsActive::get);
  }

  // ---- dashboard snapshot serialisation ----

  /**
   * Serialise the current values of these metrics into a Vert.x {@link JsonObject} for the
   * dashboard's polling endpoint. Reads the metric fields directly — the set is fixed and known, so
   * it avoids iterating the registry — and is hand-rolled so we don't pull Dropwizard's heavy Jackson
   * module.
   *
   * <p>Output shape (stable across releases — the dashboard's JS depends on it):
   *
   * <pre>{@code
   * {
   *   "ts": 1716745200000,
   *   "counters": { "proxy.http.requests.total": 42, ... },
   *   "gauges":   { "proxy.http.requests.in_flight": 3, ... },
   *   "meters":   { "proxy.http.bytes.up": { "count": 12345, "m1": 100.0, ... }, ... },
   *   "timers":   { "proxy.http.request.duration": { "count": 42, "p50": 12.3, ... }, ... }
   * }
   * }</pre>
   *
   * <p>Timer durations are emitted in <b>milliseconds</b>; rates are events-per-second.
   */
  public JsonObject toJson() {
    JsonObject counters =
        new JsonObject()
            .put("proxy.http.requests.total", httpRequestsTotal.getCount())
            .put("proxy.http.requests.failed", httpRequestsFailed.getCount())
            .put("proxy.http.requests.direct", httpRequestsDirect.getCount())
            .put("proxy.http.requests.remote", httpRequestsRemote.getCount())
            .put("proxy.http.responses.2xx", httpResponse2xx.getCount())
            .put("proxy.http.responses.3xx", httpResponse3xx.getCount())
            .put("proxy.http.responses.4xx", httpResponse4xx.getCount())
            .put("proxy.http.responses.5xx", httpResponse5xx.getCount())
            .put("proxy.https.tunnels.opened", httpsTunnelsOpened.getCount())
            .put("proxy.https.tunnels.failed", httpsTunnelsFailed.getCount())
            .put("proxy.https.tunnels.direct", httpsDirectTunnels.getCount())
            .put("proxy.https.tunnels.remote", httpsRemoteTunnels.getCount());
    JsonObject gauges =
        new JsonObject()
            .put("proxy.http.requests.in_flight", httpInFlight.get())
            .put("proxy.https.tunnels.active", httpsActive.get());
    JsonObject meters =
        new JsonObject()
            .put("proxy.http.bytes.up", meterJson(httpBytesUp))
            .put("proxy.http.bytes.down", meterJson(httpBytesDown))
            .put("proxy.https.bytes.up", meterJson(httpsBytesUp))
            .put("proxy.https.bytes.down", meterJson(httpsBytesDown));
    JsonObject timers =
        new JsonObject()
            .put("proxy.http.request.duration", timerJson(httpRequestDuration))
            .put("proxy.https.connect.duration", timerJson(httpsConnectDuration));
    return new JsonObject()
        .put("ts", System.currentTimeMillis())
        .put("counters", counters)
        .put("gauges", gauges)
        .put("meters", meters)
        .put("timers", timers);
  }

  private static JsonObject meterJson(Meter m) {
    return new JsonObject()
        .put("count", m.getCount())
        .put("m1", m.getOneMinuteRate())
        .put("m5", m.getFiveMinuteRate())
        .put("m15", m.getFifteenMinuteRate())
        .put("mean", m.getMeanRate());
  }

  private static JsonObject timerJson(Timer t) {
    Snapshot s = t.getSnapshot();
    return new JsonObject()
        .put("count", t.getCount())
        .put("m1", t.getOneMinuteRate())
        .put("m5", t.getFiveMinuteRate())
        .put("m15", t.getFifteenMinuteRate())
        .put("meanRate", t.getMeanRate())
        // Durations are nanos in Snapshot; emit millis.
        .put("p50", toMillis(s.getMedian()))
        .put("p75", toMillis(s.get75thPercentile()))
        .put("p95", toMillis(s.get95thPercentile()))
        .put("p99", toMillis(s.get99thPercentile()))
        .put("min", toMillis(s.getMin()))
        .put("max", toMillis(s.getMax()))
        .put("mean", toMillis(s.getMean()));
  }

  private static double toMillis(double nanos) {
    return nanos / (double) TimeUnit.MILLISECONDS.toNanos(1);
  }

  public void httpInFlightInc() {
    httpInFlight.incrementAndGet();
  }

  // ---- dashboard snapshot serialisation ----

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
