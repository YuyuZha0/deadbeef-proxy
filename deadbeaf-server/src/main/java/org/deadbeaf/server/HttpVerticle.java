package org.deadbeaf.server;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.auth.ProxyAuthenticationValidator;
import org.deadbeaf.bootstrap.ProxyVerticle;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class HttpVerticle extends ProxyVerticle {

  public HttpVerticle(JsonObject config) {
    super(config);
  }

  @Override
  public void start(Promise<Void> startPromise) {
    HttpClient httpClient = getVertx().createHttpClient(clientOptions());
    HttpServer httpServer = getVertx().createHttpServer(serverOptions());

    Handler<HttpServerRequest> requestHandler =
        new Http2HttpHandler(
            getVertx(),
            httpClient,
            ProxyAuthenticationValidator.fromJsonArray(
                getConfig().getJsonArray("auth"), "secretId", "secretKey"));
    httpServer.requestHandler(requestHandler);

    registerCloseHook(httpServer::close);
    registerCloseHook(httpClient::close);

    httpServer.listen(
        getConfig().getInteger("httpPort", 14483),
        result -> {
          if (result.succeeded()) {
            log.info("Start http server listening on port: {}", result.result().actualPort());
            startPromise.tryComplete();
          } else {
            startPromise.tryFail(result.cause());
          }
        });
  }

  private HttpServerOptions serverOptions() {
    JsonObject serverOptions = getConfig().getJsonObject("httpServer");
    if (serverOptions != null) {
      return new HttpServerOptions(serverOptions);
    } else {
      return enableTcpOptimizationWhenAvailable(
          new HttpServerOptions().setUseAlpn(true).setDecompressionSupported(true));
    }
  }

  private HttpClientOptions clientOptions() {
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
}
