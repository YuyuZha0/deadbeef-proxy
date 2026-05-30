package org.deadbeef.route;

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
 * End-to-end comparison of the production matcher against a plain synchronous regex matcher, over
 * the same ~120 glob patterns:
 *
 * <ul>
 *   <li><b>hyperscanMatcher</b> — the real {@link HostNameMatcherImpl#match}, now synchronous with
 *       a per-thread ({@code FastThreadLocal}) Hyperscan scanner — no context hop, no Future;
 *   <li><b>regexSynchronous</b> — a combined-alternation {@link Pattern} matched inline.
 * </ul>
 *
 * Both run on the calling thread, so this is an apples-to-apples synchronous comparison of the two
 * engines as wired into production. Single-threaded ({@code @Threads(1)}); not run by Surefire, run
 * with the JMH harness (e.g. {@code org.openjdk.jmh.Main HostNameMatcherEndToEndBenchmark}).
 *
 * <p>Measured (arm64, JDK 21, Vectorscan; 120 patterns, 8 lookups/op, average time):
 *
 * <pre>
 *   hyperscanMatcher     8.6 us/op   (~1.08 us/lookup)
 *   regexSynchronous    37.2 us/op   (~4.6 us/lookup)
 * </pre>
 *
 * The synchronous thread-local design runs at the raw Hyperscan scan speed end-to-end (~1
 * us/lookup) and is ~4.3x faster than the combined regex — a full reversal of the earlier async
 * single-context path (53 us/op), which lost to the regex once the per-call context hop was
 * counted.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 1,
    jvmArgsAppend = {
      "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
      "-Dio.netty.tryReflectionSetAccessible=true"
    })
@Threads(1)
@State(Scope.Benchmark)
public class HostNameMatcherEndToEndBenchmark {

  private static final String[] QUERIES = {
    "edge.cdn5.example.com",
    "a.b.cdn40.example.com",
    "static.site12.org",
    "host7.internal",
    "ax3.test",
    "cdn5.example.com",
    "totally.unrelated.net",
    "example.com",
  };

  private final List<String> patternStrings = HostNameMatcherBenchmark.buildPatterns();

  private HostNameMatcher hyperscanMatcher;
  private Pattern combinedRegex;

  @Setup(Level.Trial)
  public void setUp() {
    hyperscanMatcher = HostNameMatcher.create(patternStrings);

    StringBuilder alternation = new StringBuilder("^(?:");
    for (int i = 0; i < patternStrings.size(); i++) {
      String regex = HostNameMatcherImpl.fnMatchToRegex(patternStrings.get(i));
      if (i > 0) {
        alternation.append('|');
      }
      alternation.append(regex, 1, regex.length() - 1); // strip per-pattern ^..$
    }
    alternation.append(")$");
    combinedRegex = Pattern.compile(alternation.toString(), Pattern.CASE_INSENSITIVE);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    hyperscanMatcher.close(); // frees the native Hyperscan database
  }

  @Benchmark
  public void hyperscanMatcher(Blackhole bh) {
    for (String host : QUERIES) {
      bh.consume(hyperscanMatcher.match(host));
    }
  }

  @Benchmark
  public void regexSynchronous(Blackhole bh) {
    for (String host : QUERIES) {
      bh.consume(combinedRegex.matcher(host).matches());
    }
  }
}
