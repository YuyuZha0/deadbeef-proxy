package org.deadbeef.client;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MetricsSnapshotTest {

  @Test
  public void emptyRegistryProducesEmptyBucketsAndTimestamp() {
    JsonObject json = MetricsSnapshot.toJson(new MetricRegistry());
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
    JsonObject json = MetricsSnapshot.toJson(r);
    assertEquals(
        42L, (long) json.getJsonObject("counters").getLong("proxy.http.requests.total"));
  }

  @Test
  public void numericGaugeIsExposed() {
    MetricRegistry r = new MetricRegistry();
    r.register("proxy.http.requests.in_flight", (Gauge<Integer>) () -> 7);
    JsonObject json = MetricsSnapshot.toJson(r);
    assertEquals(
        7, (int) json.getJsonObject("gauges").getInteger("proxy.http.requests.in_flight"));
  }

  @Test
  public void stringGaugeFallsBackToToString() {
    MetricRegistry r = new MetricRegistry();
    r.register("proxy.custom", (Gauge<String>) () -> "alive");
    JsonObject json = MetricsSnapshot.toJson(r);
    assertEquals("alive", json.getJsonObject("gauges").getString("proxy.custom"));
  }

  @Test
  public void meterEmitsCountAndRateFields() {
    MetricRegistry r = new MetricRegistry();
    r.meter("proxy.http.bytes.up").mark(1024);
    JsonObject json = MetricsSnapshot.toJson(r);
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

    JsonObject json = MetricsSnapshot.toJson(r);
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
    JsonObject json = MetricsSnapshot.toJson(r);
    JsonObject h = json.getJsonObject("meters").getJsonObject("proxy.size");
    assertNotNull(h);
    assertEquals(2L, (long) h.getLong("count"));
    // Histograms have no rate fields.
    assertFalse(h.containsKey("m1"));
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullRegistry() {
    MetricsSnapshot.toJson(null);
  }
}
