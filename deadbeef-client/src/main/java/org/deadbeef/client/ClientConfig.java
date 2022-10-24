package org.deadbeef.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClientOptions;
import lombok.Getter;
import org.deadbeef.bootstrap.ProxyConfig;

import java.util.List;

@Getter
public final class ClientConfig implements ProxyConfig {

  private Boolean preferNativeTransport;

  private List<String> addressResolver;
  private String remoteHost;
  private int httpPort;
  private int httpsPort;
  private int localPort;
  private String secretId;
  private String secretKey;

  @JsonProperty("httpClient")
  private HttpClientOptions httpClientOptions;

  @JsonProperty("netClient")
  private NetClientOptions netClientOptions;

  @JsonProperty("localServer")
  private HttpServerOptions httpServerOptions;

  @Override
  public void verify() {
    ProxyConfig.verifyPort(localPort, "localPort");
    ProxyConfig.verifyPort(httpPort, "httpPort");
    ProxyConfig.verifyPort(httpsPort, "httpsPort");

    ProxyConfig.verifyStringNotBlank(remoteHost, "remoteHost");
    ProxyConfig.verifyStringNotBlank(secretId, "secretId");
    ProxyConfig.verifyStringNotBlank(secretKey, "secretKey");
  }
}
