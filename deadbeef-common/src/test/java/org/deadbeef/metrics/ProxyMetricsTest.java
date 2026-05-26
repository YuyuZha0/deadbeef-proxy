package org.deadbeef.metrics;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProxyMetricsTest {

  private static final String[] EXPECTED_NAMES = {
    "proxy.http.requests.total",
    "proxy.http.requests.failed",
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
}
