package org.deadbeef;

import com.codahale.metrics.MetricRegistry;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.bootstrap.Bootstrap;
import org.deadbeef.bootstrap.ProxyVerticle;
import org.deadbeef.client.ClientConfig;
import org.deadbeef.client.ConnectTunnelHandler;
import org.deadbeef.client.HttpProxyHandler;
import org.deadbeef.client.MetricsDashboardServer;
import org.deadbeef.client.ProxyClientRequestHandler;
import org.deadbeef.client.ReachabilityGate;
import org.deadbeef.metrics.ProxyMetrics;
import org.deadbeef.route.HostNameMatcher;
import org.deadbeef.route.OriginProvider;

@Slf4j
public final class App extends ProxyVerticle<ClientConfig> {

  private static final int DEFAULT_TIMEOUT_IN_MILLS = (int) TimeUnit.SECONDS.toMillis(10);

  private final MetricRegistry metricRegistry = new MetricRegistry();
  private final ProxyMetrics proxyMetrics = new ProxyMetrics(metricRegistry);

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
                        .setDecompressionSupported(true)
                        .setConnectTimeout(DEFAULT_TIMEOUT_IN_MILLS)
                        .setReadIdleTimeout(DEFAULT_TIMEOUT_IN_MILLS)));
  }

  private NetClient createNetClient() {
    return getVertx()
        .createNetClient(new NetClientOptions().setConnectTimeout(DEFAULT_TIMEOUT_IN_MILLS));
  }

  private HttpServer createHttpServer() {
    return getVertx()
        .createHttpServer(
            getOptionsOrDefault(getConfig().getHttpServerOptions(), HttpServerOptions::new));
  }

  @Override
  public void start(Promise<Void> startPromise) {
    ClientConfig config = getConfig();
    ProxyAuthenticationGenerator proxyAuthenticationGenerator =
        new ProxyAuthenticationGenerator(config.getSecretId(), config.getSecretKey());
    HttpClient httpClient = createHttpClient();
    NetClient netClient = createNetClient();
    HttpServer server = createHttpServer();

    // Remote proxy (static); direct targets are resolved per-request from the request-target.
    OriginProvider remoteProvider =
        OriginProvider.ofStatic(config.getRemotePort(), config.getRemoteHost());
    boolean proxyAll = config.isProxyAll();
    ReachabilityGate<HttpClientRequest> httpReachabilityGate =
        new ReachabilityGate<>(Duration.ofMinutes(5), 10_000);
    ReachabilityGate<NetSocket> tunnelReachabilityGate =
        new ReachabilityGate<>(Duration.ofMinutes(5), 10_000);

    // Rule lists: local_only -> always direct, remote_only -> always remote. Unlisted hosts fall
    // through to the ReachabilityGate. Bundled as client classpath resources.
    HostNameMatcher localOnly = HostNameMatcher.fromClasspathFile(getVertx(), "local_only.txt");
    HostNameMatcher remoteOnly = HostNameMatcher.fromClasspathFile(getVertx(), "remote_only.txt");

    Handler<HttpServerRequest> requestHandler =
        new ProxyClientRequestHandler(
            new HttpProxyHandler(
                getVertx(),
                httpClient,
                remoteProvider,
                OriginProvider.ofAuthority(80),
                httpReachabilityGate,
                localOnly,
                remoteOnly,
                proxyAll,
                proxyAuthenticationGenerator,
                proxyMetrics),
            new ConnectTunnelHandler(
                httpClient,
                netClient,
                remoteProvider,
                OriginProvider.ofAuthority(443),
                tunnelReachabilityGate,
                localOnly,
                remoteOnly,
                proxyAll,
                proxyAuthenticationGenerator,
                proxyMetrics));
    server.requestHandler(requestHandler);

    registerCloseHook(server::close);
    registerCloseHook(httpClient::close);
    registerCloseHook(netClient::close);

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

    Integer adminPort = config.getAdminPort();
    if (adminPort != null) {
      MetricsDashboardServer dashboard =
          new MetricsDashboardServer(getVertx(), proxyMetrics, adminPort);
      dashboard.start();
      registerCloseHook(dashboard::close);
    }
  }
}
