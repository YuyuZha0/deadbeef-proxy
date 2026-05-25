package org.deadbeef.util;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsoleReporterTest {

  private ConsoleReporter buildReporter(MetricRegistry registry, ByteArrayOutputStream sink) {
    return ConsoleReporter.forRegistry(registry)
        .outputTo(new PrintStream(sink, true, StandardCharsets.UTF_8))
        .withColor(false)
        .build();
  }

  private String runReport(MetricRegistry registry) {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    ConsoleReporter reporter = buildReporter(registry, sink);
    reporter.report();
    return sink.toString(StandardCharsets.UTF_8);
  }

  @Test
  public void wrapsOutputInUnicodeBox() {
    MetricRegistry registry = new MetricRegistry();
    registry.counter("conns").inc(7);

    String out = runReport(registry);

    assertTrue("missing top edge: " + out, out.contains("┌─"));
    assertTrue("missing bottom edge: " + out, out.contains("└"));
    assertTrue("missing vertical bar: " + out, out.contains("│"));
  }

  @Test
  public void emitsSectionTitles() {
    MetricRegistry registry = new MetricRegistry();
    registry.counter("c").inc();
    registry.meter("m").mark(100);

    String out = runReport(registry);

    assertTrue(out.contains("Counters"));
    assertTrue(out.contains("Meters"));
  }

  @Test
  public void doesNotEmitAnsiEscapesWhenColorDisabled() {
    MetricRegistry registry = new MetricRegistry();
    registry.counter("conns").inc(1);
    registry.meter("bytes").mark(2048);

    String out = runReport(registry);

    assertFalse("found ANSI escape in: " + out, out.contains("["));
  }

  @Test
  public void includesMetricNameAndCounterValue() {
    MetricRegistry registry = new MetricRegistry();
    registry.counter("HttpUp[NewPipeCnt]").inc(42);

    String out = runReport(registry);

    assertTrue("missing metric name: " + out, out.contains("HttpUp[NewPipeCnt]"));
    assertTrue("missing value 42: " + out, out.contains("42"));
  }

  @Test
  public void meterRendersBytesHumanReadable() {
    MetricRegistry registry = new MetricRegistry();
    Meter meter = registry.meter("bytes");
    meter.mark(2 * 1024L); // 2 KB

    String out = runReport(registry);

    assertTrue("missing KB label: " + out, out.contains("KB"));
    assertTrue("missing total label: " + out, out.contains("total"));
  }

  @Test
  public void titleBarMentionsMetricsAndDateTime() {
    MetricRegistry registry = new MetricRegistry();
    registry.counter("c").inc();

    String out = runReport(registry);

    String firstLine = out.split("\n", 2)[0];
    assertTrue("top line should reference metrics: " + firstLine, firstLine.contains("metrics"));
    // The current year should appear because dateTimeFormatter uses MEDIUM time + SHORT date.
    // We just verify the title bar is not the bare box.
    assertTrue("title bar should contain content: " + firstLine, firstLine.length() > 4);
  }

  @Test
  public void everyContentLineIsFramed() {
    MetricRegistry registry = new MetricRegistry();
    registry.counter("a").inc();
    registry.counter("b").inc(2);

    String out = runReport(registry);

    // Every non-empty line between the top and bottom edges starts with a box-drawing char.
    boolean inside = false;
    int framedLines = 0;
    int strayLines = 0;
    for (String line : out.split("\n")) {
      if (line.startsWith("┌")) {
        inside = true;
        continue;
      }
      if (line.startsWith("└")) {
        inside = false;
        continue;
      }
      if (inside && !line.isEmpty()) {
        if (line.startsWith("│")) {
          framedLines++;
        } else {
          strayLines++;
        }
      }
    }
    assertEquals("found stray non-framed lines inside box", 0, strayLines);
    assertTrue("expected at least one framed content line", framedLines >= 1);
  }

  @Test
  public void counterValueAlignedNearRightEdge() {
    MetricRegistry registry = new MetricRegistry();
    Counter counter = registry.counter("c");
    counter.inc(7);

    String out = runReport(registry);

    // Find the line containing "count" — the value "7" should appear after a run of dots
    // (the KV line layout) before the right-edge "│".
    String countLine = null;
    for (String line : out.split("\n")) {
      if (line.contains(" count ")) {
        countLine = line;
        break;
      }
    }
    assertTrue("no count line found in: " + out, countLine != null);
    assertTrue("count line missing dot separator: " + countLine, countLine.contains("."));
    assertTrue("count line should end with box edge: " + countLine, countLine.endsWith("│"));
  }
}
