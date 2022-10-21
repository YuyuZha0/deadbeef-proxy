package org.deadbeef.bootstrap;

import com.google.common.collect.ImmutableList;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.TCPSSLOptions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
public abstract class ProxyVerticle extends AbstractVerticle {

  @Getter(AccessLevel.PROTECTED)
  private final JsonObject config;

  private final List<Supplier<Future<Void>>> closeHooks = new ArrayList<>();

  protected ProxyVerticle(@NonNull JsonObject config) {
    this.config = config;
  }

  protected <T extends TCPSSLOptions> T enableTcpOptimizationWhenAvailable(T options) {
    if (vertx.isNativeTransportEnabled()) {
      options.setTcpFastOpen(true);
      options.setTcpNoDelay(true);
      options.setTcpQuickAck(true);
    }
    return options;
  }

  protected void registerCloseHook(Supplier<Future<Void>> action) {
    if (action != null) {
      ContextInternal context = (ContextInternal) super.context;
      context.execute(action, closeHooks::add);
    }
  }

  @Override
  public final void stop(Promise<Void> stopPromise) {
    context.runOnContext(
        v -> {
          if (closeHooks.isEmpty()) {
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
