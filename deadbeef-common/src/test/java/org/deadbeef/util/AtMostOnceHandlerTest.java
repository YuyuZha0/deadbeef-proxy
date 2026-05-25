package org.deadbeef.util;

import io.vertx.core.Handler;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AtMostOnceHandlerTest {

  @Test
  public void firesDelegateExactlyOnce() {
    AtomicInteger fired = new AtomicInteger();
    Handler<String> handler = new AtMostOnceHandler<>(e -> fired.incrementAndGet());

    handler.handle("first");
    handler.handle("second");

    assertEquals(1, fired.get());
  }

  @Test
  public void delegateReceivesTheFirstEvent() {
    AtomicInteger value = new AtomicInteger();
    Handler<Integer> handler = new AtMostOnceHandler<>(value::set);

    handler.handle(42);
    handler.handle(99);

    assertEquals(42, value.get());
  }

  @Test
  public void concurrentInvocationsStillRunDelegateOnce() throws InterruptedException {
    int threads = 16;
    AtomicInteger fired = new AtomicInteger();
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    Handler<Object> handler =
        new AtMostOnceHandler<>(
            e -> {
              fired.incrementAndGet();
            });

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      pool.submit(
          () -> {
            ready.countDown();
            try {
              start.await();
            } catch (InterruptedException ignore) {
              Thread.currentThread().interrupt();
            }
            handler.handle("e");
            done.countDown();
          });
    }
    assertTrue(ready.await(2, TimeUnit.SECONDS));
    start.countDown();
    assertTrue(done.await(5, TimeUnit.SECONDS));
    pool.shutdownNow();

    assertEquals(1, fired.get());
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullDelegate() {
    new AtMostOnceHandler<>(null);
  }
}
