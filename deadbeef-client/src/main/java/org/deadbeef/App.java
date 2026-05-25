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
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.bootstrap.Bootstrap;
import org.deadbeef.bootstrap.ProxyVerticle;
import org.deadbeef.client.ClientConfig;
import org.deadbeef.client.ConnectTunnelHandler;
import org.deadbeef.client.HttpProxyHandler;
import org.deadbeef.client.ProxyClientRequestHandler;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
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
    Bootstrap.bootstrap(App::new, args, ClientConfig.class);
  }

  private HttpClient createHttpClient() {
    return getVertx()
        .createHttpClient(
            getOptionsOrDefault(
                getConfig().getHttpClientOptions(),
                () ->
                    new HttpClientOptions()
                        .setProtocolVersion(HttpVersion.HTTP_1_1)
                        .setMaxPoolSize(128)
                        .setTryUseCompression(true)
                        .setConnectTimeout(DEFAULT_TIMEOUT_IN_MILLS)
                        .setReadIdleTimeout(DEFAULT_TIMEOUT_IN_MILLS)));
  }

  private HttpServer createHttpServer() {
    return getVertx()
        .createHttpServer(
            getOptionsOrDefault(getConfig().getHttpServerOptions(), HttpServerOptions::new));
  }

  private ConsoleReporter startReporter() {
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
    HttpServer server = createHttpServer();
    AddressPicker remotePicker = AddressPicker.ofStatic(config.getRemotePort(), config.getRemoteHost());
    Handler<HttpServerRequest> requestHandler =
        new ProxyClientRequestHandler(
            new HttpProxyHandler(
                getVertx(), httpClient, remotePicker, proxyAuthenticationGenerator, metricRegistry),
            new ConnectTunnelHandler(
                httpClient, remotePicker, proxyAuthenticationGenerator, metricRegistry));
    server.requestHandler(requestHandler);

    registerCloseHook(server::close);
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
