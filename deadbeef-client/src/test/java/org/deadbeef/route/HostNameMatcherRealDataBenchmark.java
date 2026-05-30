package org.deadbeef.route;

import com.gliwka.hyperscan.wrapper.Database;
import com.gliwka.hyperscan.wrapper.Expression;
import com.gliwka.hyperscan.wrapper.Scanner;
import com.google.common.io.CharStreams;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Hyperscan vs {@code java.util.Pattern} over the REAL bundled routing lists ({@code
 * local_only.txt} + {@code remote_only.txt}, ~hundreds of glob patterns combined), matching a
 * realistic mix of domestic, blocked, and unlisted hostnames. Same shape as the synthetic
 * benchmarks; reuses {@link HostNameMatcherImpl#fnMatchToRegex} so all engines match identical
 * semantics. Not run by Surefire; run with {@code org.openjdk.jmh.Main
 * HostNameMatcherRealDataBenchmark}.
 *
 * <p>Measured (arm64, JDK 21, Vectorscan; 436 combined local_only+remote_only patterns, 12
 * lookups/op, average time):
 *
 * <pre>
 *   hyperscan             12.5 us/op   (~1.0 us/lookup)
 *   patternAlternation    95.6 us/op   (~8.0 us/lookup)
 *   patternLoop          159.0 us/op   (~13.2 us/lookup)
 * </pre>
 *
 * Over the real 436-pattern dataset Hyperscan is ~7.6x faster than the combined regex and ~12.7x
 * faster than the per-pattern loop, and stays ~1 us/lookup (flat) while the loop scales linearly
 * with pattern count.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1) // the shared Hyperscan Scanner is not thread-safe; never raise this
@State(Scope.Benchmark)
public class HostNameMatcherRealDataBenchmark {

  /** Mix of domestic (local_only), blocked (remote_only), and unlisted hosts. */
  private static final String[] QUERIES = {
    "www.baidu.com",
    "api.weibo.com",
    "item.jd.com",
    "buy.tmall.com",
    "www.google.com",
    "i.ytimg.com",
    "scontent.fbcdn.net",
    "api.twitter.com",
    "cdn.jsdelivr.net", // unlisted
    "example.org", // unlisted
    "registry.npmjs.org", // unlisted
    "news.ycombinator.com", // unlisted
  };

  private Database database;
  private Scanner scanner;
  private List<Pattern> patterns;
  private Pattern combined;

  private static List<String> loadPatterns(String resource) throws Exception {
    try (InputStream in =
            HostNameMatcherRealDataBenchmark.class.getClassLoader().getResourceAsStream(resource);
        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
      return CharStreams.readLines(reader).stream()
          .map(StringUtils::trimToEmpty)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .toList();
    }
  }

  @Setup(Level.Trial)
  public void setUp() throws Exception {
    List<String> patternStrings = new ArrayList<>();
    patternStrings.addAll(loadPatterns("local_only.txt"));
    patternStrings.addAll(loadPatterns("remote_only.txt"));

    List<Expression> expressions = new ArrayList<>(patternStrings.size());
    patterns = new ArrayList<>(patternStrings.size());
    StringBuilder alternation = new StringBuilder("^(?:");
    for (int i = 0; i < patternStrings.size(); i++) {
      String regex = HostNameMatcherImpl.fnMatchToRegex(patternStrings.get(i));
      expressions.add(HostNameMatcherImpl.mapToExpression(patternStrings.get(i)));
      patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
      if (i > 0) {
        alternation.append('|');
      }
      alternation.append(regex, 1, regex.length() - 1); // strip per-pattern ^..$
    }
    alternation.append(")$");
    combined = Pattern.compile(alternation.toString(), Pattern.CASE_INSENSITIVE);
    database = Database.compile(expressions);
    scanner = new Scanner();
    scanner.allocScratch(database);
  }

  @TearDown(Level.Trial)
  public void tearDown() throws Exception {
    if (scanner != null) {
      scanner.close();
    }
    if (database != null) {
      database.close();
    }
  }

  @Benchmark
  public void hyperscan(Blackhole bh) {
    for (String host : QUERIES) {
      bh.consume(scanner.hasMatch(database, host));
    }
  }

  @Benchmark
  public void patternLoop(Blackhole bh) {
    for (String host : QUERIES) {
      bh.consume(anyMatch(host));
    }
  }

  @Benchmark
  public void patternAlternation(Blackhole bh) {
    for (String host : QUERIES) {
      bh.consume(combined.matcher(host).matches());
    }
  }

  private boolean anyMatch(String host) {
    for (Pattern pattern : patterns) {
      if (pattern.matcher(host).matches()) {
        return true;
      }
    }
    return false;
  }
}
