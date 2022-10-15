package org.deadbeaf;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.client.ClientHttpHandler;
import org.deadbeaf.client.ClientHttpsHandler;
import org.deadbeaf.client.ProxyClientRequestHandler;
import org.deadbeaf.route.AddressPicker;
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
  private HttpClient client;

  App(@NonNull JsonObject config) {
    this.config = config;
  }

  public static void main(String[] args) {

    JsonObject config = new JsonObject().put("remoteHost", "127.0.0.1").put("remotePort", 34273);
    // JsonObject config = Utils.loadConfig(args[0]);
    if (log.isDebugEnabled()) {
      log.debug("Load config successfully:{}{}", System.lineSeparator(), config.toString());
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

  private HttpClientOptions clientOptions() {
    JsonObject clientOptions = config.getJsonObject("proxyClient");
    if (clientOptions != null) {
      return new HttpClientOptions(clientOptions);
    } else {
      return Utils.enableTcpOptimizationWhenAvailable(
          getVertx(),
          new HttpClientOptions()
              .setMaxPoolSize(128)
              .setTryUseCompression(true)
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
    HttpClient client = getVertx().createHttpClient(clientOptions());
    HttpServer server = getVertx().createHttpServer(serverOptions());
    Handler<HttpServerRequest> requestHandler =
        new ProxyClientRequestHandler(
            new ClientHttpHandler(getVertx(), client, addressPicker),
            new ClientHttpsHandler(client, addressPicker));
    server.requestHandler(requestHandler);

    this.client = client;
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
      server.close(
          result -> {
            if (client != null) {
              client.close(stopPromise);
            } else {
              stopPromise.handle(result);
            }
          });
    } else if (client != null) {
      client.close(stopPromise);
    } else {
      stopPromise.tryComplete();
    }
  }
}
