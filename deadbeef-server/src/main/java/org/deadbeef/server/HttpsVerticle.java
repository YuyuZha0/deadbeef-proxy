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
import org.deadbeef.streams.DefaultPipeFactory;

@Slf4j
public final class HttpsVerticle extends ProxyVerticle<ServerConfig> {

  public HttpsVerticle(ServerConfig config) {
    super(config);
  }

  private NetClient createNetClient() {
    return getVertx()
        .createNetClient(
            getOptionsOrDefault(
                getConfig().getNetClientOptions(),
                () -> new NetClientOptions().setConnectTimeout(10_000)));
  }

  private NetServer createNetServer() {
    return getVertx()
        .createNetServer(
            getOptionsOrDefault(getConfig().getNetServerOptions(), NetServerOptions::new));
  }

  @Override
  public void start(Promise<Void> startPromise) {
    ServerConfig config = getConfig();
    NetServer netServer = createNetServer();
    NetClient netClient = createNetClient();
    Handler<NetSocket> connectHandler =
        new Socket2SocketHandler(
            getVertx(),
            netClient,
            ProxyAuthenticationValidator.fromEntries(config.getAuth()),
            new DefaultPipeFactory());
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
