package org.deadbeaf.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.net.NetSocket;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
final class SocketTunnel {

  private final NetSocket alice;
  private final NetSocket bob;

  SocketTunnel(@NonNull NetSocket alice, @NonNull NetSocket bob) {
    Preconditions.checkArgument(alice != bob);
    this.alice = alice;
    this.bob = bob;
  }

  private void handleWriteResult(AsyncResult<Void> ack) {
    if (ack.failed()) {
      log.error("Write socket with exception: ", ack.cause());
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
    log.warn("Unexpected exception: ", cause);
  }

  private void handleClose(NetSocket src, NetSocket dest) {
    String name = Strings.lenientFormat("[%s => %s]", src.remoteAddress(), dest.remoteAddress());
    AtomicBoolean closed = new AtomicBoolean(false);
    src.closeHandler(
        v -> {
          if (!closed.compareAndSet(false, true)) {
            return;
          }
          dest.closeHandler(null);
          dest.close();
          if (log.isDebugEnabled()) {
            log.debug("Closing tunnel: {}", name);
          }
        });
  }
}
