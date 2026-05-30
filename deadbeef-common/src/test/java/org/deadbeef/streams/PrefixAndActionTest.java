package org.deadbeef.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.mockito.Mockito;

public class PrefixAndActionTest {

  @SuppressWarnings("unchecked")
  private static WriteStream<Buffer> mockSink() {
    return (WriteStream<Buffer>) Mockito.mock(WriteStream.class);
  }

  @Test
  public void getReturnsPrefix() {
    Buffer prefix = Buffer.buffer("hello");
    PrefixAndAction<WriteStream<Buffer>> p =
        new PrefixAndAction<>(prefix, (ws, h) -> h.handle(Future.succeededFuture()));

    assertSame(prefix, p.get());
  }

  @Test
  public void applyRunsActionOnce() {
    AtomicInteger fired = new AtomicInteger();
    PrefixAndAction<WriteStream<Buffer>> p =
        new PrefixAndAction<>(
            Buffer.buffer(),
            (ws, h) -> {
              fired.incrementAndGet();
              h.handle(Future.succeededFuture());
            });

    Future<Void> first = p.apply(mockSink());
    Future<Void> second = p.apply(mockSink());

    assertEquals(1, fired.get());
    assertTrue(first.succeeded());
    assertFalse(second.succeeded());
    assertEquals("Action can not be called multiple times!", second.cause().getMessage());
  }

  @Test
  public void applyFailsFutureWhenActionThrows() {
    PrefixAndAction<WriteStream<Buffer>> p =
        new PrefixAndAction<>(
            Buffer.buffer(),
            (ws, h) -> {
              throw new RuntimeException("boom");
            });

    Future<Void> result = p.apply(mockSink());
    assertTrue(result.failed());
    assertEquals("boom", result.cause().getMessage());
  }

  @Test
  public void acceptRunsActionOnce() {
    AtomicInteger fired = new AtomicInteger();
    AtomicReference<AsyncResult<Void>> result = new AtomicReference<>();
    PrefixAndAction<WriteStream<Buffer>> p =
        new PrefixAndAction<>(
            Buffer.buffer(),
            (ws, h) -> {
              fired.incrementAndGet();
              h.handle(Future.succeededFuture());
            });

    p.accept(mockSink(), result::set);
    assertTrue(result.get().succeeded());

    p.accept(mockSink(), result::set);
    assertEquals(1, fired.get());
    assertTrue(result.get().failed());
  }

  @Test(expected = IllegalStateException.class)
  public void acceptWithNullHandlerThrowsOnRepeat() {
    PrefixAndAction<WriteStream<Buffer>> p =
        new PrefixAndAction<>(Buffer.buffer(), (ws, h) -> h.handle(Future.succeededFuture()));

    p.accept(mockSink(), null);
    p.accept(mockSink(), null);
  }

  @Test
  public void acceptWithNullHandlerForwardsErrorToEmptyHandler() {
    PrefixAndAction<WriteStream<Buffer>> p =
        new PrefixAndAction<>(
            Buffer.buffer(),
            (ws, h) -> {
              throw new RuntimeException("rethrow");
            });
    try {
      p.accept(mockSink(), null);
      fail("Should have thrown");
    } catch (RuntimeException expected) {
      assertEquals("rethrow", expected.getMessage());
    }
  }
}
