package org.deadbeef.route;

import com.gliwka.hyperscan.wrapper.Database;
import com.gliwka.hyperscan.wrapper.Expression;
import com.gliwka.hyperscan.wrapper.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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
 * Compares host-name matching against ~120 glob patterns with three strategies:
 *
 * <ul>
 *   <li><b>hyperscan</b> — one {@link Scanner#hasMatch} pass over the compiled database;
 *   <li><b>patternLoop</b> — iterate a {@code List<Pattern>} until one matches (naive baseline);
 *   <li><b>patternAlternation</b> — one combined {@code (?:..|..)} {@link Pattern}.
 * </ul>
 *
 * All three are fed regexes derived from the same {@link HostNameMatcherImpl#fnMatchToRegex} so the
 * matching semantics are identical. Not run by Surefire; run with the JMH harness, e.g. {@code java
 * -cp <test-classpath> org.openjdk.jmh.Main HostNameMatcherBenchmark} or the IDE's JMH plugin.
 *
 * <p>Measured (arm64, JDK 21, Vectorscan; 120 patterns, 16 lookups/op, average time):
 *
 * <pre>
 *   hyperscan             15.8 us/op   (~1.0 us/lookup)
 *   patternAlternation    91.6 us/op   (~5.7 us/lookup)
 *   patternLoop          118.5 us/op   (~7.4 us/lookup)
 * </pre>
 *
 * Hyperscan is ~6x faster than the combined regex and ~8x faster than the per-pattern loop, and
 * stays roughly flat as the pattern count grows while the loop scales linearly.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1) // the shared Hyperscan Scanner is not thread-safe; never raise this
@State(Scope.Benchmark)
public class HostNameMatcherBenchmark {

  /** A representative mix of hosts: some hit a pattern, some miss entirely. */
  private static final String[] QUERIES = {
    "edge.cdn5.example.com", // matches *.cdn5.example.com
    "a.b.cdn40.example.com", // matches *.cdn40.example.com (multi-label)
    "static.site12.org", // matches *.site12.org
    "host7.internal", // exact match
    "ax3.test", // matches a?3.test
    "www.cdn59.example.com", // matches *.cdn59.example.com
    "img.site39.org", // matches *.site39.org
    "cdn5.example.com", // miss: apex, not a sub-domain
    "host7.internal.evil.com", // miss: exact pattern is anchored
    "totally.unrelated.net", // miss
    "site12.org", // miss: apex
    "example.com", // miss
    "abc.test", // miss: a?3.test needs the digit
    "node.cdn200.example.com", // miss: only cdn0..59 exist
    "deep.sub.domain.unmatched.io", // miss
    "another.miss.org", // miss
  };

  private final List<String> patternStrings = buildPatterns();

  private Database database;
  private Scanner scanner;
  private List<Pattern> patterns;
  private Pattern combined;

  static List<String> buildPatterns() {
    List<String> p = new ArrayList<>(120);
    for (int i = 0; i < 60; i++) {
      p.add("*.cdn" + i + ".example.com");
    }
    for (int i = 0; i < 40; i++) {
      p.add("*.site" + i + ".org");
    }
    for (int i = 0; i < 15; i++) {
      p.add("host" + i + ".internal");
    }
    for (int i = 0; i < 5; i++) {
      p.add("a?" + i + ".test");
    }
    return p; // 120 patterns
  }

  @Setup(Level.Trial)
  public void setUp() throws Exception {
    List<Expression> expressions = new ArrayList<>(patternStrings.size());
    patterns = new ArrayList<>(patternStrings.size());
    StringBuilder alternation = new StringBuilder("^(?:");
    for (int i = 0; i < patternStrings.size(); i++) {
      String regex = HostNameMatcherImpl.fnMatchToRegex(patternStrings.get(i));
      expressions.add(HostNameMatcherImpl.mapToExpression(patternStrings.get(i)));
      patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
      // Strip the per-pattern ^..$ anchors and OR the bodies under a single anchor.
      String body = regex.substring(1, regex.length() - 1);
      if (i > 0) {
        alternation.append('|');
      }
      alternation.append(body);
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
