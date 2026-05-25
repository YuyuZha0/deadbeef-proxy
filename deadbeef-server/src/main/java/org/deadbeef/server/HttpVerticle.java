package org.deadbeef.server;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.auth.ProxyAuthenticationValidator;
import org.deadbeef.bootstrap.ProxyVerticle;
import org.deadbeef.streams.DefaultPipeFactory;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class HttpVerticle extends ProxyVerticle<ServerConfig> {

  private static final int DEFAULT_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(10);

  public HttpVerticle(ServerConfig config) {
    super(config);
  }

  private HttpClient createHttpClient() {
    return getVertx()
        .createHttpClient(
            getOptionsOrDefault(
                getConfig().getHttpClientOptions(),
                () ->
                    new HttpClientOptions()
                        .setMaxPoolSize(128)
                        .setConnectTimeout(DEFAULT_TIMEOUT)
                        .setReadIdleTimeout(DEFAULT_TIMEOUT)));
  }

  private NetClient createNetClient() {
    return getVertx()
        .createNetClient(
            getOptionsOrDefault(
                getConfig().getNetClientOptions(),
                () -> new NetClientOptions().setConnectTimeout(DEFAULT_TIMEOUT)));
  }

  private HttpServer createHttpServer() {
    return getVertx()
        .createHttpServer(
            getOptionsOrDefault(
                getConfig().getHttpServerOptions(),
                () -> new HttpServerOptions().setDecompressionSupported(true)));
  }

  @Override
  public void start(Promise<Void> startPromise) {
    ServerConfig config = getConfig();

    HttpClient httpClient = createHttpClient();
    NetClient netClient = createNetClient();
    HttpServer httpServer = createHttpServer();

    ProxyAuthenticationValidator validator =
        ProxyAuthenticationValidator.fromEntries(config.getAuth());
    DefaultPipeFactory pipeFactory = new DefaultPipeFactory();

    Handler<HttpServerRequest> proxyHandler =
        new Http2HttpHandler(getVertx(), httpClient, validator, pipeFactory);
    Handler<HttpServerRequest> connectHandler =
        new ServerConnectHandler(netClient, validator, pipeFactory);

    httpServer.requestHandler(
        request -> {
          if (request.method() == HttpMethod.CONNECT) {
            connectHandler.handle(request);
          } else {
            proxyHandler.handle(request);
          }
        });

    registerCloseHook(httpServer::close);
    registerCloseHook(httpClient::close);
    registerCloseHook(netClient::close);

    httpServer.listen(
        config.getHttpPort(),
        result -> {
          if (result.succeeded()) {
            log.info("Start proxy server listening on port: {}", result.result().actualPort());
            startPromise.tryComplete();
          } else {
            startPromise.tryFail(result.cause());
          }
        });
  }
}
