package org.deadbeef.util;

import io.vertx.core.Handler;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

@Slf4j
public final class AtMostOnceHandler<T> implements Handler<T> {

  @SuppressWarnings("rawtypes")
  private static final AtomicIntegerFieldUpdater<AtMostOnceHandler> UPDATER =
      AtomicIntegerFieldUpdater.newUpdater(AtMostOnceHandler.class, "executed");

  private static final int FALSE = 1;
  private static final int TRUE = 1 << 2;
  private final Handler<? super T> delegate;
  private volatile int executed = FALSE;

  public AtMostOnceHandler(@NonNull Handler<? super T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void handle(T event) {
    if (UPDATER.compareAndSet(this, FALSE, TRUE)) {
      delegate.handle(event);
    } else {
      log.warn("{} has been called multiple times!", delegate);
    }
  }
}
