package org.deadbeef.util;

import com.google.common.base.Preconditions;
import io.vertx.core.Handler;
import io.vertx.core.net.NetSocket;
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
}
