package org.deadbeef.server;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.auth.ProxyAuthenticationValidator;
import org.deadbeef.bootstrap.ProxyVerticle;

@Slf4j
public final class HttpsVerticle extends ProxyVerticle<ServerConfig> {

  public HttpsVerticle(ServerConfig config) {
    super(config);
  }

  @Override
  public void start(Promise<Void> startPromise) {
    ServerConfig config = getConfig();
    NetServer netServer =
        getVertx()
            .createNetServer(
                getOptionsOrDefault(config.getNetServerOptions(), NetServerOptions::new));
    NetClient netClient =
        getVertx()
            .createNetClient(
                getOptionsOrDefault(
                    config.getNetClientOptions(),
                    () -> new NetClientOptions().setConnectTimeout(10_000)));
    Handler<NetSocket> connectHandler =
        new Socket2SocketHandler(
            getVertx(), netClient, ProxyAuthenticationValidator.fromEntries(config.getAuth()));
    netServer.connectHandler(connectHandler);

    netServer.listen(
        config.getHttpsPort(),
        result -> {
          if (result.succeeded()) {
            log.info("Start HTTPS handler listening on port: {}", result.result().actualPort());
            startPromise.tryComplete();
          } else {
            startPromise.tryFail(result.cause());
          }
        });

    registerCloseHook(netServer::close);
    registerCloseHook(netClient::close);
  }
}
