package org.deadbeef.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import lombok.Getter;
import org.deadbeef.bootstrap.ProxyConfig;

import java.util.List;
import java.util.Map;

@Getter
public final class ServerConfig implements ProxyConfig {

  private Boolean preferNativeTransport;

  private List<String> addressResolver;

  private List<AuthTuple> auth;

  private int httpPort;

  private int httpsPort;

  @JsonProperty("httpClient")
  private HttpClientOptions httpClientOptions;

  @JsonProperty("httpServer")
  private HttpServerOptions httpServerOptions;

  @JsonProperty("netClient")
  private NetClientOptions netClientOptions;

  @JsonProperty("netServer")
  private NetServerOptions netServerOptions;

  @Override
  public void verify() {
    ProxyConfig.verifyPort(httpPort, "httpPort");
    ProxyConfig.verifyPort(httpsPort, "httpsPort");

    Preconditions.checkArgument(auth != null && !auth.isEmpty(), "Empty auth list!");
  }

  public static final class AuthTuple implements Map.Entry<String, String> {

    @Getter private final String secretId;
    @Getter private final String secretKey;

    @JsonCreator
    public AuthTuple(
        @JsonProperty("secretId") String secretId, @JsonProperty("secretKey") String secretKey) {
      this.secretId = secretId;
      this.secretKey = secretKey;
    }

    @Override
    public String getKey() {
      return secretId;
    }

    @Override
    public String getValue() {
      return secretKey;
    }

    @Override
    public String setValue(String value) {
      throw new UnsupportedOperationException();
    }
  }
}
