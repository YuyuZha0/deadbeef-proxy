package org.deadbeef.server;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.auth.ProxyAuthenticationValidator;
import org.deadbeef.bootstrap.ProxyVerticle;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class HttpVerticle extends ProxyVerticle<ServerConfig> {

  private static final int DEFAULT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(10);

  public HttpVerticle(ServerConfig config) {
    super(config);
  }

  @Override
  public void start(Promise<Void> startPromise) {

    ServerConfig config = getConfig();

    HttpClient httpClient =
        getVertx()
            .createHttpClient(
                getOptionsOrDefault(
                    config.getHttpClientOptions(),
                    () ->
                        new HttpClientOptions()
                            .setMaxPoolSize(128)
                            .setConnectTimeout(DEFAULT_TIMEOUT)
                            .setReadIdleTimeout(DEFAULT_TIMEOUT)));
    HttpServer httpServer =
        getVertx()
            .createHttpServer(
                getOptionsOrDefault(
                    config.getHttpServerOptions(),
                    () ->
                        new HttpServerOptions().setUseAlpn(true).setDecompressionSupported(true)));

    Handler<HttpServerRequest> requestHandler =
        new Http2HttpHandler(
            getVertx(), httpClient, ProxyAuthenticationValidator.fromEntries(config.getAuth()));
    httpServer.requestHandler(requestHandler);

    registerCloseHook(httpServer::close);
    registerCloseHook(httpClient::close);

    httpServer.listen(
        config.getHttpPort(),
        result -> {
          if (result.succeeded()) {
            log.info("Start HTTP handler listening on port: {}", result.result().actualPort());
            startPromise.tryComplete();
          } else {
            startPromise.tryFail(result.cause());
          }
        });
  }
}
