package org.deadbeef;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.bootstrap.Bootstrap;
import org.deadbeef.bootstrap.ProxyVerticle;
import org.deadbeef.client.Http2HttpHandler;
import org.deadbeef.client.Http2SocketHandler;
import org.deadbeef.client.ProxyClientRequestHandler;
import org.deadbeef.route.AddressPicker;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class App extends ProxyVerticle {

  private static final int DEFAULT_TIMEOUT_IN_MILLS = (int) TimeUnit.SECONDS.toMillis(10);

  public App(JsonObject config) {
    super(config);
  }

  public static void main(String[] args) {
    // args = new String[] {"-c", "/Users/zhaoyuyu/IdeaProjects/deadbeaf-proxy/client_config.yaml"};
    Bootstrap.bootstrap(App::new, args);
  }

  private HttpClientOptions httpClientOptions() {
    JsonObject clientOptions = getConfig().getJsonObject("httpClient");
    if (clientOptions != null) {
      return new HttpClientOptions(clientOptions);
    } else {
      return enableTcpOptimizationWhenAvailable(
          new HttpClientOptions()
              .setUseAlpn(true)
              .setProtocolVersion(HttpVersion.HTTP_2)
              .setHttp2MaxPoolSize(16)
              .setTryUseCompression(true)
              .setConnectTimeout(DEFAULT_TIMEOUT_IN_MILLS)
              .setReadIdleTimeout(DEFAULT_TIMEOUT_IN_MILLS));
    }
  }

  private NetClientOptions netClientOptions() {
    JsonObject clientOptions = getConfig().getJsonObject("netClient");
    if (clientOptions != null) {
      return new NetClientOptions(clientOptions);
    } else {
      return enableTcpOptimizationWhenAvailable(
          new NetClientOptions()
              .setReadIdleTimeout(DEFAULT_TIMEOUT_IN_MILLS)
              .setConnectTimeout(DEFAULT_TIMEOUT_IN_MILLS));
    }
  }

  private HttpServerOptions serverOptions() {
    JsonObject serverOptions = getConfig().getJsonObject("localServer");
    if (serverOptions != null) {
      return new HttpServerOptions(serverOptions);
    } else {
      return enableTcpOptimizationWhenAvailable(new HttpServerOptions());
    }
  }

  @Override
  public void start(Promise<Void> startPromise) {
    JsonObject config = getConfig();
    ProxyAuthenticationGenerator proxyAuthenticationGenerator =
        new ProxyAuthenticationGenerator(
            config.getString("secretId"), config.getString("secretKey"));
    HttpClient httpClient = getVertx().createHttpClient(httpClientOptions());
    NetClient netClient = getVertx().createNetClient(netClientOptions());
    HttpServer server = getVertx().createHttpServer(serverOptions());
    Handler<HttpServerRequest> requestHandler =
        new ProxyClientRequestHandler(
            new Http2HttpHandler(
                getVertx(),
                httpClient,
                AddressPicker.fromConfig(config, "httpPort", "remoteHost"),
                proxyAuthenticationGenerator),
            new Http2SocketHandler(
                getVertx(),
                netClient,
                AddressPicker.fromConfig(config, "httpsPort", "remoteHost"),
                proxyAuthenticationGenerator));
    server.requestHandler(requestHandler);

    registerCloseHook(server::close);
    registerCloseHook(netClient::close);
    registerCloseHook(httpClient::close);

    server.listen(
        config.getInteger("localPort", 14482),
        result -> {
          if (result.succeeded()) {
            log.info("Start proxy client listening on port: {}", result.result().actualPort());
            startPromise.tryComplete();
          } else {
            startPromise.tryFail(result.cause());
          }
        });
  }
}
