package org.deadbeaf.server;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.auth.ProxyAuthenticationValidator;
import org.deadbeaf.bootstrap.ProxyVerticle;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class HttpsVerticle extends ProxyVerticle {

  public HttpsVerticle(JsonObject config) {
    super(config);
  }

  @Override
  public void start(Promise<Void> startPromise) {
    NetServer netServer = getVertx().createNetServer(serverOptions());
    NetClient netClient = getVertx().createNetClient(clientOptions());
    Handler<NetSocket> connectHandler =
        new Socket2SocketHandler(
            getVertx(),
            netClient,
            ProxyAuthenticationValidator.fromJsonArray(
                getConfig().getJsonArray("auth"), "secretId", "secretKey"));
    netServer.connectHandler(connectHandler);

    netServer.listen(
        getConfig().getInteger("httpsPort", 14484),
        result -> {
          if (result.succeeded()) {
            log.info("Start net socket listening on port: {}", result.result().actualPort());
            startPromise.tryComplete();
          } else {
            startPromise.tryFail(result.cause());
          }
        });

    registerCloseHook(netServer::close);
    registerCloseHook(netClient::close);
  }

  private NetServerOptions serverOptions() {
    JsonObject serverOptions = getConfig().getJsonObject("httpsServer");
    if (serverOptions != null) {
      return new NetServerOptions(serverOptions);
    } else {
      return enableTcpOptimizationWhenAvailable(new NetServerOptions());
    }
  }

  private NetClientOptions clientOptions() {
    JsonObject clientOptions = getConfig().getJsonObject("httpsClient");
    if (clientOptions != null) {
      return new NetClientOptions(clientOptions);
    } else {
      return enableTcpOptimizationWhenAvailable(
          new NetClientOptions().setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10)));
    }
  }
}
