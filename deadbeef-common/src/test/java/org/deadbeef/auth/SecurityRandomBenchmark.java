package org.deadbeef.auth;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 * <pre>
 * Benchmark                                     Mode  Cnt     Score   Error  Units
 * SecurityRandomBenchmark.nativeSecurityRandom  avgt    2  1318.766          ns/op
 * SecurityRandomBenchmark.random                avgt    2   524.076          ns/op
 * SecurityRandomBenchmark.sha1SecurityRandom    avgt    2  1093.202          ns/op
 * SecurityRandomBenchmark.threadLocalRandom     avgt    2    15.846          ns/op
 * </pre>
 */
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 2, time = 3)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(3)
@Fork(1)
public class SecurityRandomBenchmark {

  private static final int LEN = 16;
  private SecureRandom nativePRNG;
  private SecureRandom sha1PRNG;
  private Random random;

  @Setup
  public void setup() {
    try {
      nativePRNG = SecureRandom.getInstance("NativePRNG");
      sha1PRNG = SecureRandom.getInstance("Sha1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    random = new Random();
  }

  @Benchmark
  public byte[] nativeSecurityRandom() {
    byte[] bytes = new byte[LEN];
    nativePRNG.nextBytes(bytes);
    return bytes;
  }

  @Benchmark
  public byte[] sha1SecurityRandom() {
    byte[] bytes = new byte[LEN];
    sha1PRNG.nextBytes(bytes);
    return bytes;
  }

  @Benchmark
  public byte[] random() {
    byte[] bytes = new byte[LEN];
    random.nextBytes(bytes);
    return bytes;
  }

  @Benchmark
  public byte[] threadLocalRandom() {
    byte[] bytes = new byte[LEN];
    ThreadLocalRandom.current().nextBytes(bytes);
    return bytes;
  }
}
