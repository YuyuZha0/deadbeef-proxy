package org.deadbeef;

import com.codahale.metrics.MetricRegistry;
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
import org.deadbeef.util.ConsoleReporter;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class App extends ProxyVerticle<ClientConfig> {

  private static final int DEFAULT_TIMEOUT_IN_MILLS = (int) TimeUnit.SECONDS.toMillis(10);

  private final MetricRegistry metricRegistry = new MetricRegistry();

  public App(ClientConfig config) {
    super(config);
  }

  public static void main(String[] args) {
    Bootstrap.printLogo();
    // args = new String[] {"-c", "/Users/zhaoyuyu/IdeaProjects/deadbeef-proxy/client-config.yaml"};
    Bootstrap.bootstrap(App::new, args, ClientConfig.class);
  }

  private HttpClient createHttpClient() {
    return getVertx()
        .createHttpClient(
            getOptionsOrDefault(
                getConfig().getHttpClientOptions(),
                () ->
                    new HttpClientOptions()
                        .setUseAlpn(true)
                        .setProtocolVersion(HttpVersion.HTTP_2)
                        .setHttp2MaxPoolSize(16)
                        .setTryUseCompression(true)
                        .setConnectTimeout(DEFAULT_TIMEOUT_IN_MILLS)
                        .setReadIdleTimeout(DEFAULT_TIMEOUT_IN_MILLS)));
  }

  private NetClient createNetClient() {
    return getVertx()
        .createNetClient(
            getOptionsOrDefault(
                getConfig().getNetClientOptions(),
                () ->
                    new NetClientOptions()
                        .setReadIdleTimeout(DEFAULT_TIMEOUT_IN_MILLS)
                        .setConnectTimeout(DEFAULT_TIMEOUT_IN_MILLS)));
  }

  private HttpServer createHttpServer() {
    return getVertx()
        .createHttpServer(
            getOptionsOrDefault(getConfig().getHttpServerOptions(), HttpServerOptions::new));
  }

  private ConsoleReporter startReporter() {
    // hack for human friendly bytes
    ConsoleReporter consoleReporter =
        ConsoleReporter.forRegistry(metricRegistry)
            .convertDurationsTo(TimeUnit.MINUTES)
            .convertRatesTo(TimeUnit.SECONDS)
            .scheduleOn(getVertx().nettyEventLoopGroup().next())
            .shutdownExecutorOnStop(false)
            .build();
    consoleReporter.start(1, 5, TimeUnit.MINUTES);
    log.info("Console reporter started.");
    return consoleReporter;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    ClientConfig config = getConfig();
    ProxyAuthenticationGenerator proxyAuthenticationGenerator =
        new ProxyAuthenticationGenerator(config.getSecretId(), config.getSecretKey());
    HttpClient httpClient = createHttpClient();
    NetClient netClient = createNetClient();
    HttpServer server = createHttpServer();
    Handler<HttpServerRequest> requestHandler =
        new ProxyClientRequestHandler(
            new Http2HttpHandler(
                getVertx(),
                httpClient,
                AddressPicker.ofStatic(config.getHttpPort(), config.getRemoteHost()),
                proxyAuthenticationGenerator,
                metricRegistry),
            new Http2SocketHandler(
                getVertx(),
                netClient,
                AddressPicker.ofStatic(config.getHttpsPort(), config.getRemoteHost()),
                proxyAuthenticationGenerator,
                metricRegistry));
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
    ConsoleReporter consoleReporter = startReporter();
    registerCloseHookSync(consoleReporter::close);
  }
}
