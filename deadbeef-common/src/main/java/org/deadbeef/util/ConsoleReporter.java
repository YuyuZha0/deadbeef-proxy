package org.deadbeef.util;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricAttribute;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** A reporter which outputs measurements to a {@link PrintStream}, like {@code System.out}. */
public class ConsoleReporter extends ScheduledReporter {

  private static final long ONE_KB = 1 << 10;
  private static final long ONE_MB = ONE_KB << 10;
  private static final long ONE_GB = ONE_MB << 10;
  private static final long ONE_TB = ONE_GB << 10;

  private static final int CONSOLE_WIDTH = 80;
  // Inner content width between the two box vertical bars and a single space padding on each side.
  private static final int INNER_WIDTH = CONSOLE_WIDTH - 4;
  // Column at which the right edge of a key/value's value sits within the inner content area.
  private static final int VALUE_COLUMN = INNER_WIDTH - 4;

  private static final String TOP_LEFT = "┌";
  private static final String TOP_RIGHT = "┐";
  private static final String BOTTOM_LEFT = "└";
  private static final String BOTTOM_RIGHT = "┘";
  private static final String VERTICAL = "│";
  private static final String HORIZONTAL = "─";

  private final PrintStream output;
  private final Locale locale;
  private final Clock clock;
  private final DateTimeFormatter dateTimeFormatter;
  private final ZoneId zoneId;
  private final Ansi ansi;

  ConsoleReporter(
      MetricRegistry registry,
      PrintStream output,
      Locale locale,
      Clock clock,
      ZoneId zoneId,
      TimeUnit rateUnit,
      TimeUnit durationUnit,
      MetricFilter filter,
      ScheduledExecutorService executor,
      boolean shutdownExecutorOnStop,
      Set<MetricAttribute> disabledMetricAttributes,
      boolean colorize) {
    super(
        registry,
        "console-reporter",
        filter,
        rateUnit,
        durationUnit,
        executor,
        shutdownExecutorOnStop,
        disabledMetricAttributes);
    this.output = output;
    this.locale = locale;
    this.clock = clock;
    this.dateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM);
    this.zoneId = zoneId;
    this.ansi = colorize ? Ansi.ENABLED : Ansi.DISABLED;
  }

  /**
   * Returns a new {@link Builder} for {@link ConsoleReporter}.
   *
   * @param registry the registry to report
   * @return a {@link Builder} instance for a {@link ConsoleReporter}
   */
  public static Builder forRegistry(MetricRegistry registry) {
    return new Builder(registry);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public void report(
      SortedMap<String, Gauge> gauges,
      SortedMap<String, Counter> counters,
      SortedMap<String, Histogram> histograms,
      SortedMap<String, Meter> meters,
      SortedMap<String, Timer> timers) {
    final String dateTime =
        dateTimeFormatter.format(Instant.ofEpochMilli(clock.getTime()).atZone(zoneId));

    printBoxTop("metrics · " + dateTime);

    boolean firstSection = true;
    if (!gauges.isEmpty()) {
      printSectionTitle("Gauges", firstSection);
      firstSection = false;
      for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
        printMetricName(entry.getKey());
        printGauge(entry.getValue());
      }
    }
    if (!counters.isEmpty()) {
      printSectionTitle("Counters", firstSection);
      firstSection = false;
      for (Map.Entry<String, Counter> entry : counters.entrySet()) {
        printCounter(entry.getKey(), entry.getValue());
      }
    }
    if (!histograms.isEmpty()) {
      printSectionTitle("Histograms", firstSection);
      firstSection = false;
      for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
        printMetricName(entry.getKey());
        printHistogram(entry.getValue());
      }
    }
    if (!meters.isEmpty()) {
      printSectionTitle("Meters", firstSection);
      firstSection = false;
      for (Map.Entry<String, Meter> entry : meters.entrySet()) {
        printMetricName(entry.getKey());
        printMeter(entry.getValue());
      }
    }
    if (!timers.isEmpty()) {
      printSectionTitle("Timers", firstSection);
      firstSection = false;
      for (Map.Entry<String, Timer> entry : timers.entrySet()) {
        printMetricName(entry.getKey());
        printTimer(entry.getValue());
      }
    }

    printBoxBottom();
    output.println();
    output.flush();
  }

  private String humanReadableBytes(double d) {
    if (d < ONE_KB) {
      return new DecimalFormat("#.# bytes").format(d);
    }
    if (d < ONE_MB) {
      return new DecimalFormat("#.# KB").format(d / ONE_KB);
    }
    if (d < ONE_GB) {
      return new DecimalFormat("#.## MB").format(d / ONE_MB);
    }
    if (d < ONE_TB) {
      return new DecimalFormat("#.## GB").format(d / ONE_GB);
    }
    return new DecimalFormat("#.## TB").format(d / ONE_TB);
  }

  private void printGauge(Gauge<?> gauge) {
    printKV("value", String.valueOf(gauge.getValue()));
  }

  private void printCounter(String name, Counter counter) {
    // Counters are single-value metrics; render name and value on a single visual row when short.
    printMetricName(name);
    printKV("count", String.format(locale, "%d", counter.getCount()));
  }

  private void printMeter(Meter meter) {
    printKVIfEnabled(MetricAttribute.COUNT, "total", humanReadableBytes(meter.getCount()));
    printKVIfEnabled(
        MetricAttribute.MEAN_RATE,
        "mean rate",
        humanReadableBytes(meter.getMeanRate()) + "/" + getRateUnit());
    printKVIfEnabled(
        MetricAttribute.M1_RATE,
        "1-minute rate",
        humanReadableBytes(meter.getOneMinuteRate()) + "/" + getRateUnit());
    printKVIfEnabled(
        MetricAttribute.M5_RATE,
        "5-minute rate",
        humanReadableBytes(meter.getFiveMinuteRate()) + "/" + getRateUnit());
    printKVIfEnabled(
        MetricAttribute.M15_RATE,
        "15-minute rate",
        humanReadableBytes(meter.getFifteenMinuteRate()) + "/" + getRateUnit());
  }

  private void printHistogram(Histogram histogram) {
    printKVIfEnabled(MetricAttribute.COUNT, "count", String.format(locale, "%d", histogram.getCount()));
    Snapshot snapshot = histogram.getSnapshot();
    printKVIfEnabled(MetricAttribute.MIN, "min", String.format(locale, "%d", snapshot.getMin()));
    printKVIfEnabled(MetricAttribute.MAX, "max", String.format(locale, "%d", snapshot.getMax()));
    printKVIfEnabled(MetricAttribute.MEAN, "mean", String.format(locale, "%2.2f", snapshot.getMean()));
    printKVIfEnabled(MetricAttribute.STDDEV, "stddev", String.format(locale, "%2.2f", snapshot.getStdDev()));
    printKVIfEnabled(MetricAttribute.P50, "median", String.format(locale, "%2.2f", snapshot.getMedian()));
    printKVIfEnabled(MetricAttribute.P75, "75%", String.format(locale, "%2.2f", snapshot.get75thPercentile()));
    printKVIfEnabled(MetricAttribute.P95, "95%", String.format(locale, "%2.2f", snapshot.get95thPercentile()));
    printKVIfEnabled(MetricAttribute.P98, "98%", String.format(locale, "%2.2f", snapshot.get98thPercentile()));
    printKVIfEnabled(MetricAttribute.P99, "99%", String.format(locale, "%2.2f", snapshot.get99thPercentile()));
    printKVIfEnabled(MetricAttribute.P999, "99.9%", String.format(locale, "%2.2f", snapshot.get999thPercentile()));
  }

  private void printTimer(Timer timer) {
    Snapshot snapshot = timer.getSnapshot();
    String d = getDurationUnit();
    String r = getRateUnit();
    printKVIfEnabled(MetricAttribute.COUNT, "count", String.format(locale, "%d", timer.getCount()));
    printKVIfEnabled(
        MetricAttribute.MEAN_RATE,
        "mean rate",
        String.format(locale, "%2.2f calls/%s", convertRate(timer.getMeanRate()), r));
    printKVIfEnabled(
        MetricAttribute.M1_RATE,
        "1-minute rate",
        String.format(locale, "%2.2f calls/%s", convertRate(timer.getOneMinuteRate()), r));
    printKVIfEnabled(
        MetricAttribute.M5_RATE,
        "5-minute rate",
        String.format(locale, "%2.2f calls/%s", convertRate(timer.getFiveMinuteRate()), r));
    printKVIfEnabled(
        MetricAttribute.M15_RATE,
        "15-minute rate",
        String.format(locale, "%2.2f calls/%s", convertRate(timer.getFifteenMinuteRate()), r));
    printKVIfEnabled(
        MetricAttribute.MIN,
        "min",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.getMin()), d));
    printKVIfEnabled(
        MetricAttribute.MAX,
        "max",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.getMax()), d));
    printKVIfEnabled(
        MetricAttribute.MEAN,
        "mean",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.getMean()), d));
    printKVIfEnabled(
        MetricAttribute.STDDEV,
        "stddev",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.getStdDev()), d));
    printKVIfEnabled(
        MetricAttribute.P50,
        "median",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.getMedian()), d));
    printKVIfEnabled(
        MetricAttribute.P75,
        "75%",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.get75thPercentile()), d));
    printKVIfEnabled(
        MetricAttribute.P95,
        "95%",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.get95thPercentile()), d));
    printKVIfEnabled(
        MetricAttribute.P98,
        "98%",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.get98thPercentile()), d));
    printKVIfEnabled(
        MetricAttribute.P99,
        "99%",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.get99thPercentile()), d));
    printKVIfEnabled(
        MetricAttribute.P999,
        "99.9%",
        String.format(locale, "%2.2f %s", convertDuration(snapshot.get999thPercentile()), d));
  }

  // ---- box-drawing helpers ----

  private void printBoxTop(String title) {
    // Title appears between two leading "─ " and trailing " " inside the top edge.
    String label = " " + title + " ";
    int dashes = CONSOLE_WIDTH - 2 - 1 - label.length();
    if (dashes < 0) {
      dashes = 0;
    }
    StringBuilder sb = new StringBuilder(CONSOLE_WIDTH);
    sb.append(ansi.dim(TOP_LEFT + HORIZONTAL));
    sb.append(ansi.bold(ansi.cyan(label)));
    for (int i = 0; i < dashes; i++) {
      sb.append(ansi.dim(HORIZONTAL));
    }
    sb.append(ansi.dim(TOP_RIGHT));
    output.println(sb.toString());
  }

  private void printBoxBottom() {
    StringBuilder sb = new StringBuilder(CONSOLE_WIDTH);
    sb.append(ansi.dim(BOTTOM_LEFT));
    for (int i = 0; i < CONSOLE_WIDTH - 2; i++) {
      sb.append(ansi.dim(HORIZONTAL));
    }
    sb.append(ansi.dim(BOTTOM_RIGHT));
    output.println(sb.toString());
  }

  /** Print a single line inside the box. {@code plain} is the unstyled visible content used for
   * width measurement; {@code styled} is what is actually emitted (may include ANSI codes). */
  private void printBoxLine(String plain, String styled) {
    int pad = INNER_WIDTH - plain.length();
    if (pad < 0) {
      pad = 0;
    }
    StringBuilder sb = new StringBuilder();
    sb.append(ansi.dim(VERTICAL)).append(' ').append(styled);
    for (int i = 0; i < pad; i++) {
      sb.append(' ');
    }
    sb.append(' ').append(ansi.dim(VERTICAL));
    output.println(sb.toString());
  }

  private void printBlankBoxLine() {
    printBoxLine("", "");
  }

  private void printSectionTitle(String name, boolean firstSection) {
    if (!firstSection) {
      printBlankBoxLine();
    }
    printBoxLine(name, ansi.bold(ansi.yellow(name)));
  }

  private void printMetricName(String name) {
    String indented = "  " + name;
    printBoxLine(indented, indented);
  }

  private void printKV(String label, String value) {
    // Layout: "    <label> .... <value>" right-aligned to VALUE_COLUMN, then padded to INNER_WIDTH.
    String indent = "    ";
    int valueStart = VALUE_COLUMN - value.length();
    int dots = valueStart - indent.length() - label.length() - 1; // -1 for the space after label
    if (dots < 1) {
      dots = 1;
    }
    StringBuilder plain = new StringBuilder(INNER_WIDTH);
    plain.append(indent).append(label).append(' ');
    for (int i = 0; i < dots; i++) {
      plain.append('.');
    }
    plain.append(' ').append(value);
    printBoxLine(plain.toString(), plain.toString());
  }

  private void printKVIfEnabled(MetricAttribute type, String label, String value) {
    if (getDisabledMetricAttributes().contains(type)) {
      return;
    }
    printKV(label, value);
  }

  // ---- ANSI helper ----

  private static final class Ansi {
    static final Ansi ENABLED = new Ansi(true);
    static final Ansi DISABLED = new Ansi(false);

    private static final String RESET = "[0m";
    private static final String DIM = "[2m";
    private static final String BOLD = "[1m";
    private static final String CYAN = "[36m";
    private static final String YELLOW = "[33m";

    private final boolean on;

    private Ansi(boolean on) {
      this.on = on;
    }

    String dim(String text) {
      return on ? DIM + text + RESET : text;
    }

    String bold(String text) {
      return on ? BOLD + text + RESET : text;
    }

    String cyan(String text) {
      return on ? CYAN + text + RESET : text;
    }

    String yellow(String text) {
      return on ? YELLOW + text + RESET : text;
    }
  }

  /**
   * A builder for {@link com.codahale.metrics.ConsoleReporter} instances. Defaults to using the
   * default locale and time zone, writing to {@code System.out}, converting rates to events/second,
   * converting durations to milliseconds, and not filtering metrics.
   */
  public static class Builder {
    private final MetricRegistry registry;
    private PrintStream output;
    private Locale locale;
    private Clock clock;
    private ZoneId zoneId;
    private TimeUnit rateUnit;
    private TimeUnit durationUnit;
    private MetricFilter filter;
    private ScheduledExecutorService executor;
    private boolean shutdownExecutorOnStop;
    private Set<MetricAttribute> disabledMetricAttributes;
    private Boolean colorize;

    private Builder(MetricRegistry registry) {
      this.registry = registry;
      this.output = System.out;
      this.locale = Locale.getDefault();
      this.clock = Clock.defaultClock();
      this.zoneId = ZoneId.systemDefault();
      this.rateUnit = TimeUnit.SECONDS;
      this.durationUnit = TimeUnit.MILLISECONDS;
      this.filter = MetricFilter.ALL;
      this.executor = null;
      this.shutdownExecutorOnStop = true;
      this.disabledMetricAttributes = Collections.emptySet();
      this.colorize = null;
    }

    public Builder shutdownExecutorOnStop(boolean shutdownExecutorOnStop) {
      this.shutdownExecutorOnStop = shutdownExecutorOnStop;
      return this;
    }

    public Builder scheduleOn(ScheduledExecutorService executor) {
      this.executor = executor;
      return this;
    }

    public Builder outputTo(PrintStream output) {
      this.output = output;
      return this;
    }

    public Builder formattedFor(Locale locale) {
      this.locale = locale;
      return this;
    }

    public Builder withClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    public Builder formattedFor(ZoneId zoneId) {
      this.zoneId = zoneId;
      return this;
    }

    public Builder convertRatesTo(TimeUnit rateUnit) {
      this.rateUnit = rateUnit;
      return this;
    }

    public Builder convertDurationsTo(TimeUnit durationUnit) {
      this.durationUnit = durationUnit;
      return this;
    }

    public Builder filter(MetricFilter filter) {
      this.filter = filter;
      return this;
    }

    public Builder disabledMetricAttributes(Set<MetricAttribute> disabledMetricAttributes) {
      this.disabledMetricAttributes = disabledMetricAttributes;
      return this;
    }

    /** Visible for testing — force ANSI color emission on or off. Null leaves auto-detection. */
    Builder withColor(Boolean colorize) {
      this.colorize = colorize;
      return this;
    }

    public ConsoleReporter build() {
      boolean useColor = colorize != null ? colorize : System.console() != null;
      return new ConsoleReporter(
          registry,
          output,
          locale,
          clock,
          zoneId,
          rateUnit,
          durationUnit,
          filter,
          executor,
          shutdownExecutorOnStop,
          disabledMetricAttributes,
          useColor);
    }
  }
}
