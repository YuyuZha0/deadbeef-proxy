package org.deadbeaf;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.auth.ProxyAuthenticationValidator;
import org.deadbeaf.server.ProxyServerRequestHandler;
import org.deadbeaf.server.ServerHttpHandler;
import org.deadbeaf.server.ServerHttpsHandler;
import org.deadbeaf.util.Constants;
import org.deadbeaf.util.Utils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class App extends AbstractVerticle {

  static {
    System.setProperty(
        "vertx.logger-delegate-factory-class-name", SLF4JLogDelegateFactory.class.getName());
    InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
  }

  private final JsonObject config;

  private HttpServer server;
  private HttpClient httpClient;
  private NetClient netClient;

  App(@NonNull JsonObject config) {
    this.config = config;
  }

  public static void main(String[] args) {
    JsonObject config = Utils.loadConfig(args[0]);
    if (log.isDebugEnabled()) {
      log.debug("Load config successfully:{}{}", Constants.lineSeparator(), config);
    }
    Vertx vertx =
        Vertx.vertx(
            new VertxOptions()
                .setPreferNativeTransport(
                    config.getBoolean("preferNativeTransport", Boolean.TRUE)));
    vertx.deployVerticle(
        () -> new App(config),
        new DeploymentOptions(),
        result -> {
          String deployID = result.result();
          if (result.succeeded()) {
            log.info("Deploy verticle successfully: {}", deployID);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> vertx.undeploy(deployID)));
          } else {
            log.error("Deploy verticle with unexpected exception: ", result.cause());
          }
        });
  }

  private ProxyAuthenticationValidator validator() {
    JsonArray entries = config.getJsonArray("auth");
    int len = entries.size();
    List<Map.Entry<String, String>> list = new ArrayList<>();
    for (int i = 0; i < len; ++i) {
      JsonObject object = entries.getJsonObject(i);
      if (object != null) {
        list.add(
            new AbstractMap.SimpleImmutableEntry<>(
                object.getString("secretId"), object.getString("secretKey")));
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
            validator());
    server.requestHandler(requestHandler);

    this.httpClient = httpClient;
    this.netClient = netClient;
    this.server = server;

    server.listen(
        result -> {
          if (result.succeeded()) {
            log.info("Start Http server listening on port: {}", result.result().actualPort());
            startPromise.tryComplete();
          } else {
            startPromise.tryFail(result.cause());
          }
        });
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    if (server != null) {
      server.close(result -> closeClients(stopPromise));
    } else {
      closeClients(stopPromise);
    }
  }

  private void closeClients(Promise<Void> promise) {
    HttpClient httpClient = this.httpClient;
    NetClient netClient = this.netClient;
    if (httpClient != null && netClient != null) {
      CompositeFuture.all(httpClient.close(), netClient.close())
          .onComplete(
              result -> {
                if (result.succeeded()) {
                  promise.tryComplete();
                } else {
                  promise.tryFail(result.cause());
                }
              });
      return;
    }
    if (httpClient != null) {
      httpClient.close(promise);
      return;
    }
    if (netClient != null) {
      netClient.close(promise);
      return;
    }
    promise.tryComplete();
  }

  private HttpClientOptions httpClientOptions() {
    JsonObject clientOptions = config.getJsonObject("httpClient");
    if (clientOptions != null) {
      return new HttpClientOptions(clientOptions);
    } else {
      return Utils.enableTcpOptimizationWhenAvailable(
          getVertx(),
          new HttpClientOptions()
              .setMaxPoolSize(128)
              .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10))
              .setReadIdleTimeout((int) TimeUnit.SECONDS.toMillis(10)));
    }
  }

  private NetClientOptions netClientOptions() {
    JsonObject clientOptions = config.getJsonObject("netClient");
    if (clientOptions != null) {
      return new NetClientOptions(clientOptions);
    } else {
      return Utils.enableTcpOptimizationWhenAvailable(
          getVertx(),
          new NetClientOptions()
              .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10))
              .setReadIdleTimeout((int) TimeUnit.SECONDS.toMillis(10)));
    }
  }

  private HttpServerOptions serverOptions() {
    JsonObject serverOptions = config.getJsonObject("proxyServer");
    if (serverOptions != null) {
      return new HttpServerOptions(serverOptions);
    } else {
      return Utils.enableTcpOptimizationWhenAvailable(
          getVertx(),
          new HttpServerOptions().setUseAlpn(true).setDecompressionSupported(true).setPort(34273));
    }
  }
}
