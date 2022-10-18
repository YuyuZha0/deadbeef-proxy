package org.deadbeaf.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.net.NetSocket;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class SocketTunnel {

  private final NetSocket alice;
  private final NetSocket bob;

  private final String name;

  SocketTunnel(@NonNull NetSocket alice, @NonNull NetSocket bob) {
    Preconditions.checkArgument(alice != bob);
    this.alice = alice;
    this.bob = bob;
    this.name =
        Strings.lenientFormat("TUNNEL[%s <==> %s]", alice.remoteAddress(), bob.remoteAddress());
  }

  private void handleWriteResult(AsyncResult<Void> ack) {
    if (ack.failed()) {
      log.error("{}| Write socket with exception: ", name, ack.cause());
    }
  }

  private void registerForwarding(NetSocket src, NetSocket dest) {
    Handler<Void> drainHandler = v -> src.resume();
    src.handler(
        buffer -> {
          dest.write(buffer, this::handleWriteResult);
          if (dest.writeQueueFull()) {
            src.pause();
            dest.drainHandler(drainHandler);
          }
        });
    src.endHandler(v -> dest.end());
    src.resume();
  }

  void open() {
    alice.exceptionHandler(this::handleException);
    bob.exceptionHandler(this::handleException);
    registerForwarding(alice, bob);
    registerForwarding(bob, alice);
    handleClose(alice, bob);
    handleClose(bob, alice);
  }

  private void handleException(Throwable cause) {
    if (cause instanceof NoStackTraceThrowable) {
      log.warn("{}| Unexpected exception on tunnel: {}", name, cause.getMessage());
    } else {
      log.warn("{}| Unexpected exception on tunnel: ", name, cause);
    }
  }

  private void handleClose(NetSocket src, NetSocket dest) {
    src.closeHandler(
        Utils.atMostOnce(
            v -> {
              dest.closeHandler(null);
              dest.close();
              if (log.isDebugEnabled()) {
                log.debug("Closing tunnel: {}", name);
              }
            }));
  }
}
