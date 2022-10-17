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
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.auth.ProxyAuthenticationGenerator;
import org.deadbeaf.client.ClientHttpHandler;
import org.deadbeaf.client.ClientHttpsHandler;
import org.deadbeaf.client.ProxyClientRequestHandler;
import org.deadbeaf.route.AddressPicker;
import org.deadbeaf.util.Constants;
import org.deadbeaf.util.Utils;

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
  private HttpClient httpsClient;

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

  private HttpClientOptions httpClientOptions() {
    JsonObject clientOptions = config.getJsonObject("httpClient");
    if (clientOptions != null) {
      return new HttpClientOptions(clientOptions);
    } else {
      return Utils.enableTcpOptimizationWhenAvailable(
          getVertx(),
          new HttpClientOptions()
              .setUseAlpn(true)
              .setProtocolVersion(HttpVersion.HTTP_2)
              .setHttp2MaxPoolSize(16)
              .setTryUseCompression(true)
              .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10))
              .setReadIdleTimeout((int) TimeUnit.SECONDS.toMillis(10)));
    }
  }

  private HttpClientOptions httpsClientOptions() {
    JsonObject clientOptions = config.getJsonObject("httpsClient");
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

  private HttpServerOptions serverOptions() {
    JsonObject serverOptions = config.getJsonObject("localServer");
    if (serverOptions != null) {
      return new HttpServerOptions(serverOptions);
    } else {
      return Utils.enableTcpOptimizationWhenAvailable(
          getVertx(), new HttpServerOptions().setPort(34081));
    }
  }

  @Override
  public void start(Promise<Void> startPromise) {
    AddressPicker addressPicker =
        AddressPicker.ofStatic(config.getInteger("remotePort", -1), config.getString("remoteHost"));
    ProxyAuthenticationGenerator proxyAuthenticationGenerator =
        new ProxyAuthenticationGenerator(
            config.getString("secretId"), config.getString("secretKey"));
    HttpClient httpClient = getVertx().createHttpClient(httpClientOptions());
    HttpClient httpsClient = getVertx().createHttpClient(httpsClientOptions());
    HttpServer server = getVertx().createHttpServer(serverOptions());
    Handler<HttpServerRequest> requestHandler =
        new ProxyClientRequestHandler(
            new ClientHttpHandler(
                getVertx(), httpClient, addressPicker, proxyAuthenticationGenerator),
            new ClientHttpsHandler(httpsClient, addressPicker, proxyAuthenticationGenerator));
    server.requestHandler(requestHandler);

    this.httpClient = httpClient;
    this.httpsClient = httpsClient;
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
    HttpClient httpsClient = this.httpsClient;
    if (httpClient != null && httpsClient != null) {
      CompositeFuture.all(httpClient.close(), httpsClient.close())
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
    if (httpsClient != null) {
      httpsClient.close(promise);
      return;
    }
    promise.tryComplete();
  }
}
