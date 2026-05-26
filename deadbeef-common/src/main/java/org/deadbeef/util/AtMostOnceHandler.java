package org.deadbeef.util;

import io.vertx.core.Handler;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class AtMostOnceHandler<T> implements Handler<T> {

  @SuppressWarnings("rawtypes")
  private static final AtomicIntegerFieldUpdater<AtMostOnceHandler> UPDATER =
      AtomicIntegerFieldUpdater.newUpdater(AtMostOnceHandler.class, "executed");

  private static final int FALSE = 1;
  private static final int TRUE = 1 << 2;
  private final Handler<? super T> delegate;
  private final boolean verbose;
  private volatile int executed = FALSE;

  public AtMostOnceHandler(Handler<? super T> delegate) {
    this(delegate, false);
  }

  public AtMostOnceHandler(@NonNull Handler<? super T> delegate, boolean verbose) {

    this.delegate = delegate;
    this.verbose = verbose;
  }

  @Override
  public void handle(T event) {
    if (UPDATER.compareAndSet(this, FALSE, TRUE)) {
      delegate.handle(event);
    } else if (verbose) {
      log.warn("{} has been called multiple times!", delegate);
    }
  }
}
