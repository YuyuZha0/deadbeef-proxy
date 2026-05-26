package org.deadbeef.client;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;

/**
 * Serialises a {@link MetricRegistry} snapshot into a Vert.x {@link JsonObject} for the dashboard's
 * polling endpoint. Hand-rolled so we don't pull Dropwizard's heavy Jackson module.
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
 * <p>Timer durations are emitted in <b>milliseconds</b> regardless of the timer's internal time
 * unit; rates are events-per-second.
 */
public final class MetricsSnapshot {

  private MetricsSnapshot() {
    throw new IllegalStateException();
  }

  public static JsonObject toJson(@NonNull MetricRegistry registry) {
    JsonObject root = new JsonObject();
    root.put("ts", System.currentTimeMillis());
    root.put("counters", counters(registry));
    root.put("gauges", gauges(registry));
    root.put("meters", meters(registry));
    root.put("timers", timers(registry));
    return root;
  }

  private static JsonObject counters(MetricRegistry registry) {
    JsonObject out = new JsonObject();
    for (Map.Entry<String, Counter> e : registry.getCounters().entrySet()) {
      out.put(e.getKey(), e.getValue().getCount());
    }
    return out;
  }

  @SuppressWarnings("rawtypes")
  private static JsonObject gauges(MetricRegistry registry) {
    JsonObject out = new JsonObject();
    for (Map.Entry<String, Gauge> e : registry.getGauges().entrySet()) {
      Object value = e.getValue().getValue();
      if (value instanceof Number n) {
        out.put(e.getKey(), n);
      } else if (value != null) {
        out.put(e.getKey(), value.toString());
      }
    }
    return out;
  }

  private static JsonObject meters(MetricRegistry registry) {
    JsonObject out = new JsonObject();
    for (Map.Entry<String, Meter> e : registry.getMeters().entrySet()) {
      Meter m = e.getValue();
      out.put(
          e.getKey(),
          new JsonObject()
              .put("count", m.getCount())
              .put("m1", m.getOneMinuteRate())
              .put("m5", m.getFiveMinuteRate())
              .put("m15", m.getFifteenMinuteRate())
              .put("mean", m.getMeanRate()));
    }
    // Histograms folded into the same bucket as a degenerate case (count + snapshot, no rates).
    for (Map.Entry<String, Histogram> e : registry.getHistograms().entrySet()) {
      Histogram h = e.getValue();
      Snapshot s = h.getSnapshot();
      out.put(
          e.getKey(),
          new JsonObject()
              .put("count", h.getCount())
              .put("min", s.getMin())
              .put("max", s.getMax())
              .put("mean", s.getMean())
              .put("p50", s.getMedian())
              .put("p95", s.get95thPercentile())
              .put("p99", s.get99thPercentile()));
    }
    return out;
  }

  private static JsonObject timers(MetricRegistry registry) {
    JsonObject out = new JsonObject();
    for (Map.Entry<String, Timer> e : registry.getTimers().entrySet()) {
      Timer t = e.getValue();
      Snapshot s = t.getSnapshot();
      out.put(
          e.getKey(),
          new JsonObject()
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
              .put("mean", toMillis(s.getMean())));
    }
    return out;
  }

  private static double toMillis(double nanos) {
    return nanos / (double) TimeUnit.MILLISECONDS.toNanos(1);
  }
}
