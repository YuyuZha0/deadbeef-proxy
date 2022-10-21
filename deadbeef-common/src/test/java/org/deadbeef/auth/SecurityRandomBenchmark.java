package org.deadbeef.auth;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 * <pre>
 *     Benchmark                                  Mode  Cnt     Score    Error  Units
 * SecurityRandomBenchmark.random             avgt   25   583.628 ± 39.325  ns/op
 * SecurityRandomBenchmark.securityRandom     avgt   25  1327.705 ±  8.572  ns/op
 * SecurityRandomBenchmark.threadLocalRandom  avgt   25    15.877 ±  0.029  ns/op
 * </pre>
 */
@Measurement()
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(3)
public class SecurityRandomBenchmark {

  private static final int LEN = 16;
  private final SecureRandom SECURE_RANDOM = new SecureRandom();
  private final Random RANDOM = new Random();

  @Benchmark
  public byte[] securityRandom() {
    byte[] bytes = new byte[LEN];
    SECURE_RANDOM.nextBytes(bytes);
    return bytes;
  }

  @Benchmark
  public byte[] random() {
    byte[] bytes = new byte[LEN];
    RANDOM.nextBytes(bytes);
    return bytes;
  }

  @Benchmark
  public byte[] threadLocalRandom() {
    byte[] bytes = new byte[LEN];
    ThreadLocalRandom.current().nextBytes(bytes);
    return bytes;
  }
}
