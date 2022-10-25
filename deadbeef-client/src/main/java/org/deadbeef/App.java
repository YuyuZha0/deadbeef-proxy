package org.deadbeef;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.bootstrap.Bootstrap;
import org.deadbeef.bootstrap.ProxyVerticle;
import org.deadbeef.client.ClientConfig;
import org.deadbeef.client.Http2HttpHandler;
import org.deadbeef.client.Http2SocketHandler;
import org.deadbeef.client.ProxyClientRequestHandler;
import org.deadbeef.route.AddressPicker;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class App extends ProxyVerticle<ClientConfig> {

  private static final int DEFAULT_TIMEOUT_IN_MILLS = (int) TimeUnit.SECONDS.toMillis(10);

  public App(ClientConfig config) {
    super(config);
  }

  public static void main(String[] args) {
    Bootstrap.printLogo();
    // args = new String[] {"-c", "/Users/zhaoyuyu/IdeaProjects/deadbeef-proxy/client-config.yaml"};
    Bootstrap.bootstrap(App::new, args, ClientConfig.class);
  }

  @Override
  public void start(Promise<Void> startPromise) {
    ClientConfig config = getConfig();
    ProxyAuthenticationGenerator proxyAuthenticationGenerator =
        new ProxyAuthenticationGenerator(config.getSecretId(), config.getSecretKey());
    HttpClient httpClient =
        getVertx()
            .createHttpClient(
                getOptionsOrDefault(
                    config.getHttpClientOptions(),
                    () ->
                        new HttpClientOptions()
                            .setUseAlpn(true)
                            .setProtocolVersion(HttpVersion.HTTP_2)
                            .setHttp2MaxPoolSize(16)
                            .setTryUseCompression(true)
                            .setConnectTimeout(DEFAULT_TIMEOUT_IN_MILLS)
                            .setReadIdleTimeout(DEFAULT_TIMEOUT_IN_MILLS)));
    NetClient netClient =
        getVertx()
            .createNetClient(
                getOptionsOrDefault(
                    config.getNetClientOptions(),
                    () ->
                        new NetClientOptions()
                            .setReadIdleTimeout(DEFAULT_TIMEOUT_IN_MILLS)
                            .setConnectTimeout(DEFAULT_TIMEOUT_IN_MILLS)));
    HttpServer server =
        getVertx()
            .createHttpServer(
                getOptionsOrDefault(config.getHttpServerOptions(), HttpServerOptions::new));
    Handler<HttpServerRequest> requestHandler =
        new ProxyClientRequestHandler(
            new Http2HttpHandler(
                getVertx(),
                httpClient,
                AddressPicker.ofStatic(config.getHttpPort(), config.getRemoteHost()),
                proxyAuthenticationGenerator),
            new Http2SocketHandler(
                getVertx(),
                netClient,
                AddressPicker.ofStatic(config.getHttpsPort(), config.getRemoteHost()),
                proxyAuthenticationGenerator));
    server.requestHandler(requestHandler);

    registerCloseHook(server::close);
    registerCloseHook(netClient::close);
    registerCloseHook(httpClient::close);

    server.listen(
        config.getLocalPort(),
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
