package org.deadbeef.client;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import lombok.NonNull;

/**
 * Single-flight reachability gate for "try direct, fall back to remote" routing.
 *
 * <p>The first caller for a given {@link SocketAddress} runs the supplied probe; concurrent callers
 * wait on it, and the verdict (success or failure) is cached for the configured window so that a
 * known-unreachable address fast-fails into the fallback path instead of being re-probed on every
 * request. On a cached success the gate issues a fresh attempt per caller; the very first caller
 * additionally reuses the probe's own result so the probe connection is not wasted.
 *
 * <p>The {@code supplier} may be invoked more than once (the first-caller probe, plus a fresh
 * attempt for concurrent / later callers), so it must be re-callable — {@code httpClient.request}
 * and {@code netClient.connect} both are.
 */
public final class ReachabilityGate<T>
    implements BiFunction<SocketAddress, Supplier<? extends Future<T>>, Future<T>> {

  private final ConcurrentMap<SocketAddress, Promise<Void>> availabilityCache;

  public ReachabilityGate(@NonNull Duration expireDuration, long maxSize) {
    this.availabilityCache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(expireDuration)
            .<SocketAddress, Promise<Void>>build()
            .asMap();
  }

  @Override
  public Future<T> apply(SocketAddress socketAddress, Supplier<? extends Future<T>> supplier) {
    @SuppressWarnings("unchecked")
    final Future<T>[] slot = new Future[1];
    // Happens-before: the slot write is ordered before the promise completion that triggers the
    // compose below, which reads it.
    return availabilityCache
        .computeIfAbsent(
            socketAddress,
            addr -> {
              Promise<Void> availabilityPromise = Promise.promise();
              supplier
                  .get()
                  .onComplete(
                      ar -> {
                        if (ar.succeeded()) {
                          slot[0] = Future.succeededFuture(ar.result());
                          availabilityPromise.tryComplete();
                        } else {
                          slot[0] = Future.failedFuture(ar.cause());
                          availabilityPromise.tryFail(ar.cause());
                        }
                      });
              return availabilityPromise;
            })
        .future()
        .compose(
            v -> {
              Future<T> res = slot[0];
              return res == null ? supplier.get() : res;
            });
  }
}
