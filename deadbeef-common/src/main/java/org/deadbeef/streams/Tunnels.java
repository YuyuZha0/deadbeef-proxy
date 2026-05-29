package org.deadbeef.streams;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.NetSocket;
import lombok.NonNull;
import org.deadbeef.util.Utils;

/**
 * Shared CONNECT-tunnel plumbing: upgrade the inbound HTTP request to a raw {@link NetSocket} and
 * pipe bytes both ways, closing both halves once (and only once) when either drops. Used by the
 * server's CONNECT handler and by the client for both its direct and remote tunnels, so the
 * close-coupling lives in one place.
 */
public final class Tunnels {

  private Tunnels() {
    throw new IllegalStateException();
  }

  /**
   * @param serverRequest the inbound CONNECT request to upgrade
   * @param upstream the already-connected upstream socket (direct target or remote proxy)
   * @param clientToUpstream pipe factory for browser → upstream traffic
   * @param upstreamToClient pipe factory for upstream → browser traffic
   * @param onUpgraded invoked once after a successful socket upgrade (nullable; e.g. gauge inc)
   * @param onClose invoked once when either side closes (nullable; e.g. gauge dec)
   * @param onError invoked if the upgrade itself fails (nullable)
   */
  public static void upgrade(
      @NonNull HttpServerRequest serverRequest,
      @NonNull NetSocket upstream,
      @NonNull PipeFactory clientToUpstream,
      @NonNull PipeFactory upstreamToClient,
      Handler<Void> onUpgraded,
      Handler<Void> onClose,
      Handler<Throwable> onError) {
    serverRequest.toNetSocket(
        ar -> {
          if (ar.failed()) {
            if (onError != null) {
              onError.handle(ar.cause());
            }
            upstream.close();
            return;
          }
          NetSocket downstream = ar.result();
          if (onUpgraded != null) {
            onUpgraded.handle(null);
          }
          Handler<Void> closeOnce =
              Utils.atMostOnce(
                  v -> {
                    if (onClose != null) {
                      onClose.handle(null);
                    }
                    downstream.close();
                    upstream.close();
                  });
          downstream.closeHandler(closeOnce);
          upstream.closeHandler(closeOnce);
          clientToUpstream.newPipe(downstream).to(upstream);
          upstreamToClient.newPipe(upstream).to(downstream);
        });
  }
}
