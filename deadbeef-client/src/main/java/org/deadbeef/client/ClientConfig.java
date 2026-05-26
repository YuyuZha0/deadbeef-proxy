package org.deadbeef.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import java.util.List;
import lombok.Getter;
import org.deadbeef.bootstrap.ProxyConfig;

@Getter
public final class ClientConfig implements ProxyConfig {

  private Boolean preferNativeTransport;

  private List<String> addressResolver;
  private String remoteHost;
  private int remotePort;
  private int localPort;

  /** Optional: when set, the client binds a metrics dashboard HttpServer on 127.0.0.1:adminPort. */
  private Integer adminPort;

  private String secretId;
  private String secretKey;

  @JsonProperty("httpClient")
  private HttpClientOptions httpClientOptions;

  @JsonProperty("localServer")
  private HttpServerOptions httpServerOptions;

  @Override
  public void verify() {
    ProxyConfig.verifyPort(localPort, "localPort");
    ProxyConfig.verifyPort(remotePort, "remotePort");
    if (adminPort != null) {
      ProxyConfig.verifyPort(adminPort, "adminPort");
    }

    ProxyConfig.verifyStringNotBlank(remoteHost, "remoteHost");
    ProxyConfig.verifyStringNotBlank(secretId, "secretId");
    ProxyConfig.verifyStringNotBlank(secretKey, "secretKey");
  }
}
