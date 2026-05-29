package org.deadbeef.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class ProxyMetricsTest {

  private static final String[] EXPECTED_NAMES = {
    "proxy.http.requests.total",
    "proxy.http.requests.failed",
    "proxy.http.requests.direct",
    "proxy.http.requests.remote",
    "proxy.http.responses.2xx",
    "proxy.http.responses.3xx",
    "proxy.http.responses.4xx",
    "proxy.http.responses.5xx",
    "proxy.http.request.duration",
    "proxy.http.bytes.up",
    "proxy.http.bytes.down",
    "proxy.http.requests.in_flight",
    "proxy.https.tunnels.opened",
    "proxy.https.tunnels.failed",
    "proxy.https.tunnels.direct",
    "proxy.https.tunnels.remote",
    "proxy.https.connect.duration",
    "proxy.https.bytes.up",
    "proxy.https.bytes.down",
    "proxy.https.tunnels.active",
  };

  @Test
  public void allMetricsRegisterEagerly() {
    MetricRegistry registry = new MetricRegistry();
    new ProxyMetrics(registry);
    for (String name : EXPECTED_NAMES) {
      assertTrue("missing metric: " + name, registry.getNames().contains(name));
    }
    assertEquals(EXPECTED_NAMES.length, registry.getNames().size());
  }

  @Test
  public void inFlightGaugesReflectCounters() {
    MetricRegistry registry = new MetricRegistry();
    ProxyMetrics m = new ProxyMetrics(registry);

    assertEquals(0, m.httpInFlight());
    m.httpInFlightInc();
    m.httpInFlightInc();
    assertEquals(2, m.httpInFlight());
    m.httpInFlightDec();
    assertEquals(1, m.httpInFlight());

    assertEquals(0, m.httpsActive());
    m.httpsActiveInc();
    assertEquals(1, m.httpsActive());
    m.httpsActiveDec();
    assertEquals(0, m.httpsActive());

    // Gauge values must come through the registry too.
    assertEquals(1, registry.getGauges().get("proxy.http.requests.in_flight").getValue());
    assertEquals(0, registry.getGauges().get("proxy.https.tunnels.active").getValue());
  }

  @Test
  public void recordHttpStatusBucketsByHundreds() {
    MetricRegistry registry = new MetricRegistry();
    ProxyMetrics m = new ProxyMetrics(registry);

    m.recordHttpStatus(200);
    m.recordHttpStatus(204);
    m.recordHttpStatus(301);
    m.recordHttpStatus(404);
    m.recordHttpStatus(401);
    m.recordHttpStatus(503);

    assertEquals(2, m.httpResponse2xx.getCount());
    assertEquals(1, m.httpResponse3xx.getCount());
    assertEquals(2, m.httpResponse4xx.getCount());
    assertEquals(1, m.httpResponse5xx.getCount());
  }

  @Test
  public void recordHttpStatusIgnoresOutOfRange() {
    MetricRegistry registry = new MetricRegistry();
    ProxyMetrics m = new ProxyMetrics(registry);
    m.recordHttpStatus(0);
    m.recordHttpStatus(100); // informational
    m.recordHttpStatus(600); // synthetic
    assertEquals(0, m.httpResponse2xx.getCount());
    assertEquals(0, m.httpResponse3xx.getCount());
    assertEquals(0, m.httpResponse4xx.getCount());
    assertEquals(0, m.httpResponse5xx.getCount());
  }

  @Test
  public void countersAreShared() {
    MetricRegistry registry = new MetricRegistry();
    ProxyMetrics m = new ProxyMetrics(registry);
    m.httpRequestsTotal.inc(5);
    assertEquals(5, registry.counter("proxy.http.requests.total").getCount());
    assertEquals(m.httpRequestsTotal, registry.counter("proxy.http.requests.total"));
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullRegistry() {
    new ProxyMetrics(null);
  }

  @Test
  public void timersAreUsable() {
    MetricRegistry registry = new MetricRegistry();
    ProxyMetrics m = new ProxyMetrics(registry);
    var ctx = m.httpRequestDuration.time();
    try {
      // brief workload
      Thread.sleep(1);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    } finally {
      ctx.stop();
    }
    assertNotNull(m.httpRequestDuration.getSnapshot());
    assertEquals(1, m.httpRequestDuration.getCount());
  }

  // ---- toJson snapshot serialisation (migrated from MetricsSnapshot) ----

  @Test
  public void emptyRegistryProducesEmptyBucketsAndTimestamp() {
    JsonObject json = ProxyMetrics.toJson(new MetricRegistry());
    assertTrue(json.getLong("ts") > 0);
    assertEquals(0, json.getJsonObject("counters").size());
    assertEquals(0, json.getJsonObject("gauges").size());
    assertEquals(0, json.getJsonObject("meters").size());
    assertEquals(0, json.getJsonObject("timers").size());
  }

  @Test
  public void counterValueIsExposed() {
    MetricRegistry r = new MetricRegistry();
    r.counter("proxy.http.requests.total").inc(42);
    JsonObject json = ProxyMetrics.toJson(r);
    assertEquals(42L, (long) json.getJsonObject("counters").getLong("proxy.http.requests.total"));
  }

  @Test
  public void numericGaugeIsExposed() {
    MetricRegistry r = new MetricRegistry();
    r.register("proxy.http.requests.in_flight", (Gauge<Integer>) () -> 7);
    JsonObject json = ProxyMetrics.toJson(r);
    assertEquals(7, (int) json.getJsonObject("gauges").getInteger("proxy.http.requests.in_flight"));
  }

  @Test
  public void stringGaugeFallsBackToToString() {
    MetricRegistry r = new MetricRegistry();
    r.register("proxy.custom", (Gauge<String>) () -> "alive");
    JsonObject json = ProxyMetrics.toJson(r);
    assertEquals("alive", json.getJsonObject("gauges").getString("proxy.custom"));
  }

  @Test
  public void meterEmitsCountAndRateFields() {
    MetricRegistry r = new MetricRegistry();
    r.meter("proxy.http.bytes.up").mark(1024);
    JsonObject json = ProxyMetrics.toJson(r);
    JsonObject meter = json.getJsonObject("meters").getJsonObject("proxy.http.bytes.up");
    assertNotNull(meter);
    assertEquals(1024L, (long) meter.getLong("count"));
    assertNotNull(meter.getValue("m1"));
    assertNotNull(meter.getValue("m5"));
    assertNotNull(meter.getValue("m15"));
    assertNotNull(meter.getValue("mean"));
  }

  @Test
  public void timerEmitsPercentilesAndRatesInMillis() {
    MetricRegistry r = new MetricRegistry();
    r.timer("proxy.http.request.duration").update(50, TimeUnit.MILLISECONDS);
    r.timer("proxy.http.request.duration").update(100, TimeUnit.MILLISECONDS);
    r.timer("proxy.http.request.duration").update(150, TimeUnit.MILLISECONDS);

    JsonObject json = ProxyMetrics.toJson(r);
    JsonObject timer = json.getJsonObject("timers").getJsonObject("proxy.http.request.duration");
    assertNotNull(timer);
    assertEquals(3L, (long) timer.getLong("count"));
    // p50 of {50,100,150} ms should be ~100 ms (allow for snapshot estimator wobble).
    double p50 = timer.getDouble("p50");
    assertTrue("p50 was " + p50, p50 >= 50.0 && p50 <= 150.0);
    // Min should be ~50ms, max ~150ms (durations emitted in ms).
    assertTrue(timer.getDouble("min") >= 49.0 && timer.getDouble("min") <= 51.0);
    assertTrue(timer.getDouble("max") >= 149.0 && timer.getDouble("max") <= 151.0);
  }

  @Test
  public void histogramFoldedIntoMeters() {
    MetricRegistry r = new MetricRegistry();
    r.histogram("proxy.size").update(10);
    r.histogram("proxy.size").update(20);
    JsonObject json = ProxyMetrics.toJson(r);
    JsonObject h = json.getJsonObject("meters").getJsonObject("proxy.size");
    assertNotNull(h);
    assertEquals(2L, (long) h.getLong("count"));
    // Histograms have no rate fields.
    assertFalse(h.containsKey("m1"));
  }

  @Test(expected = NullPointerException.class)
  public void toJsonRejectsNullRegistry() {
    ProxyMetrics.toJson(null);
  }
}
