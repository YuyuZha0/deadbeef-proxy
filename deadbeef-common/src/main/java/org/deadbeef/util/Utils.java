package org.deadbeef.util;

import com.google.common.base.Preconditions;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.impl.PipeImpl;
import lombok.NonNull;

public final class Utils {

  private Utils() {
    throw new IllegalStateException();
  }

  public static <T> Handler<T> atMostOnce(Handler<? super T> original) {
    return new AtMostOnceHandler<>(original);
  }

  public static void exchangeCloseHook(@NonNull NetSocket alice, @NonNull NetSocket bob) {
    Preconditions.checkArgument(alice != bob);
    alice.closeHandler(
        atMostOnce(
            v -> {
              bob.closeHandler(null);
              bob.close();
            }));
    bob.closeHandler(
        atMostOnce(
            v -> {
              alice.closeHandler(null);
              alice.close();
            }));
  }

  public static Pipe<Buffer> newPipe(
      ReadStream<Buffer> src, boolean endOnSuccess, boolean endOnFail) {
    return new PipeImpl<>(src).endOnSuccess(endOnSuccess).endOnFailure(endOnFail);
  }
}
