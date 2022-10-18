package org.deadbeaf;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.deadbeaf.auth.ProxyAuthenticationValidator;
import org.deadbeaf.bootstrap.Bootstrap;
import org.deadbeaf.bootstrap.ProxyVerticle;
import org.deadbeaf.server.ProxyServerRequestHandler;
import org.deadbeaf.server.ServerHttpHandler;
import org.deadbeaf.server.ServerHttpsHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class App extends ProxyVerticle {

  App(JsonObject config) {
    super(config);
  }

  public static void main(String[] args) {
    Bootstrap.bootstrap(App::new, args);
  }

  private ProxyAuthenticationValidator createValidator() {
    JsonArray entries = getConfig().getJsonArray("auth");
    int len = entries.size();
    List<Map.Entry<String, String>> list = new ArrayList<>();
    for (int i = 0; i < len; ++i) {
      JsonObject object = entries.getJsonObject(i);
      if (object != null) {
        list.add(Pair.of(object.getString("secretId"), object.getString("secretKey")));
      }
    }
    return ProxyAuthenticationValidator.fromEntries(list);
  }

  @Override
  public void start(Promise<Void> startPromise) {
    HttpClient httpClient = getVertx().createHttpClient(httpClientOptions());
    NetClient netClient = getVertx().createNetClient(netClientOptions());
    HttpServer server = getVertx().createHttpServer(serverOptions());

    Handler<HttpServerRequest> requestHandler =
        new ProxyServerRequestHandler(
            new ServerHttpHandler(vertx, httpClient),
            new ServerHttpsHandler(netClient),
            createValidator());
    server.requestHandler(requestHandler);

    registerCloseHook(server::close);
    registerCloseHook(netClient::close);
    registerCloseHook(httpClient::close);

    server.listen(
        getConfig().getInteger("severPort", 14483),
        result -> {
          if (result.succeeded()) {
            log.info("Start proxy server listening on port: {}", result.result().actualPort());
            startPromise.tryComplete();
          } else {
            startPromise.tryFail(result.cause());
          }
        });
  }

  private HttpClientOptions httpClientOptions() {
    JsonObject clientOptions = getConfig().getJsonObject("httpClient");
    if (clientOptions != null) {
      return new HttpClientOptions(clientOptions);
    } else {
      return enableTcpOptimizationWhenAvailable(
          new HttpClientOptions()
              .setMaxPoolSize(128)
              .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10))
              .setReadIdleTimeout((int) TimeUnit.SECONDS.toMillis(10)));
    }
  }

  private NetClientOptions netClientOptions() {
    JsonObject clientOptions = getConfig().getJsonObject("netClient");
    if (clientOptions != null) {
      return new NetClientOptions(clientOptions);
    } else {
      return enableTcpOptimizationWhenAvailable(
          new NetClientOptions()
              .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10))
              .setReadIdleTimeout((int) TimeUnit.SECONDS.toMillis(10)));
    }
  }

  private HttpServerOptions serverOptions() {
    JsonObject serverOptions = getConfig().getJsonObject("proxyServer");
    if (serverOptions != null) {
      return new HttpServerOptions(serverOptions);
    } else {
      return enableTcpOptimizationWhenAvailable(
          new HttpServerOptions().setUseAlpn(true).setDecompressionSupported(true));
    }
  }
}
