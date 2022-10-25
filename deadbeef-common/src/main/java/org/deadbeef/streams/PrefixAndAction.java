package org.deadbeef.streams;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;
import lombok.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PrefixAndAction<W extends WriteStream<Buffer>>
    implements Supplier<Buffer>,
        Function<W, Future<Void>>,
        BiConsumer<W, Handler<AsyncResult<Void>>> {

  private static final String ERROR_MSG = "Action can not be called multiple times!";
  private static final Handler<AsyncResult<Void>> EMPTY_HANDLER =
      result -> {
        if (result.failed()) {
          result.cause().printStackTrace();
        }
      };

  private final AtomicBoolean actionExecuted = new AtomicBoolean(false);
  private final Buffer prefix;
  private final BiConsumer<W, Handler<AsyncResult<Void>>> action;

  PrefixAndAction(Buffer prefix, BiConsumer<W, Handler<AsyncResult<Void>>> action) {
    this.prefix = prefix;
    this.action = action;
  }

  @Override
  public Future<Void> apply(@NonNull W writeStream) {
    Promise<Void> promise = Promise.promise();
    if (actionExecuted.compareAndSet(false, true)) {
      try {
        action.accept(writeStream, promise);
      } catch (Throwable cause) {
        promise.tryFail(cause);
      }
    } else {
      promise.tryFail(ERROR_MSG);
    }
    return promise.future();
  }

  @Override
  public void accept(@NonNull W writeStream, Handler<AsyncResult<Void>> handler) {
    if (actionExecuted.compareAndSet(false, true)) {
      try {
        action.accept(writeStream, handler != null ? handler : EMPTY_HANDLER);
      } catch (Throwable cause) {
        if (handler != null) {
          handler.handle(Future.failedFuture(cause));
        } else {
          throw cause;
        }
      }
    } else {
      if (handler != null) {
        handler.handle(Future.failedFuture(ERROR_MSG));
      } else {
        throw new IllegalStateException(ERROR_MSG);
      }
    }
  }

  @Override
  public Buffer get() {
    return prefix;
  }
}
