package org.deadbeef.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Test;

public class ReachabilityGateTest {

  private static final SocketAddress ADDR = SocketAddress.inetSocketAddress(8080, "example.com");

  private static <T> ReachabilityGate<T> newGate() {
    return new ReachabilityGate<>(Duration.ofMinutes(5), 100);
  }

  @Test
  public void firstSuccessReturnsProbeResult() {
    ReachabilityGate<String> gate = newGate();
    AtomicInteger calls = new AtomicInteger();
    Future<String> result =
        gate.apply(
            ADDR,
            () -> {
              calls.incrementAndGet();
              return Future.succeededFuture("ok");
            });
    assertTrue(result.succeeded());
    assertEquals("ok", result.result());
    assertEquals(1, calls.get());
  }

  @Test
  public void cachedFailureFastFailsWithoutReprobing() {
    ReachabilityGate<String> gate = newGate();
    AtomicInteger calls = new AtomicInteger();
    Supplier<Future<String>> probe =
        () -> {
          calls.incrementAndGet();
          return Future.failedFuture(new RuntimeException("down"));
        };

    Future<String> first = gate.apply(ADDR, probe);
    Future<String> second = gate.apply(ADDR, probe);

    assertTrue(first.failed());
    assertTrue(second.failed());
    // The probe runs once; the cached failure short-circuits subsequent callers.
    assertEquals(1, calls.get());
  }

  @Test
  public void successAllowsFreshAttemptPerCaller() {
    ReachabilityGate<String> gate = newGate();
    AtomicInteger calls = new AtomicInteger();
    Supplier<Future<String>> probe = () -> Future.succeededFuture("v" + calls.incrementAndGet());

    Future<String> first = gate.apply(ADDR, probe);
    Future<String> second = gate.apply(ADDR, probe);

    assertEquals("v1", first.result());
    // First caller reuses the probe; the second caller gets a fresh attempt.
    assertEquals("v2", second.result());
    assertEquals(2, calls.get());
  }

  @Test
  public void verdictIsPerAddress() {
    ReachabilityGate<String> gate = newGate();
    SocketAddress other = SocketAddress.inetSocketAddress(9090, "other.example.com");

    Future<String> down =
        gate.apply(ADDR, () -> Future.failedFuture(new RuntimeException("down")));
    Future<String> up = gate.apply(other, () -> Future.succeededFuture("ok"));

    assertTrue(down.failed());
    assertFalse(up.failed());
    assertEquals("ok", up.result());
  }
}
