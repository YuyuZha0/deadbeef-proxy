package org.deadbeef.bootstrap;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.net.TCPSSLOptions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
public abstract class ProxyVerticle<C extends ProxyConfig> extends AbstractVerticle {

  @Getter(AccessLevel.PROTECTED)
  private final C config;

  private final List<Supplier<Future<Void>>> closeHooks = new ArrayList<>();

  protected ProxyVerticle(@NonNull C config) {
    this.config = config;
  }

  protected void registerCloseHook(Supplier<Future<Void>> action) {
    if (action != null) {
      context.runOnContext(v -> closeHooks.add(action));
    }
  }

  protected void registerCloseHookSync(Runnable r) {
    if (r != null) {
      registerCloseHook(
          () -> {
            try {
              r.run();
              return Future.succeededFuture();
            } catch (Throwable cause) {
              return Future.failedFuture(cause);
            }
          });
    }
  }

  protected <T extends TCPSSLOptions> T getOptionsOrDefault(
      T options, @NonNull Supplier<? extends T> defaultSuppler) {
    if (options != null) {
      return options;
    }
    T defaultValue = defaultSuppler.get();
    Preconditions.checkNotNull(defaultValue, "default supplier can't return null!");
    if (getVertx().isNativeTransportEnabled()) {
      defaultValue.setTcpNoDelay(true).setTcpQuickAck(true).setTcpFastOpen(true);
    }
    return defaultValue;
  }

  @Override
  public final void stop(Promise<Void> stopPromise) {
    context.runOnContext(
        v -> {
          if (closeHooks.isEmpty()) {
            stopPromise.tryComplete();
            return;
          }
          List<Supplier<Future<Void>>> copy = ImmutableList.copyOf(closeHooks);
          closeHooks.clear();
          runHooksOrdered(copy, 0, stopPromise);
        });
  }

  private void runHooksOrdered(
      List<Supplier<Future<Void>>> futures, int index, Promise<Void> promise) {
    if (index >= futures.size()) {
      promise.tryComplete();
      return;
    }
    Future<Void> future = null;
    try {
      future = futures.get(index).get();
    } catch (Exception e) {
      log.error("Executing closeHook with unexpected exception: ", e);
    }
    if (future != null) {
      future.onComplete(
          result -> {
            if (result.failed()) {
              log.error("Executing closeHook with unexpected exception: ", result.cause());
            }
            runHooksOrdered(futures, index + 1, promise);
          });
    } else {
      runHooksOrdered(futures, index + 1, promise);
    }
  }
}
