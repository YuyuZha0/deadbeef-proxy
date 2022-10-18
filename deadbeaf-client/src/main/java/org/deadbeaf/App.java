package org.deadbeaf;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.auth.ProxyAuthenticationGenerator;
import org.deadbeaf.bootstrap.Bootstrap;
import org.deadbeaf.bootstrap.ProxyVerticle;
import org.deadbeaf.client.ClientHttpHandler;
import org.deadbeaf.client.ClientHttpsHandler;
import org.deadbeaf.client.ProxyClientRequestHandler;
import org.deadbeaf.route.AddressPicker;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class App extends ProxyVerticle {

  public App(JsonObject config) {
    super(config);
  }

  public static void main(String[] args) {
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
              .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10))
              .setReadIdleTimeout((int) TimeUnit.SECONDS.toMillis(10)));
    }
  }

  private HttpClientOptions httpsClientOptions() {
    JsonObject clientOptions = getConfig().getJsonObject("httpsClient");
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

    registerCloseHook(server::close);
    registerCloseHook(httpsClient::close);
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
