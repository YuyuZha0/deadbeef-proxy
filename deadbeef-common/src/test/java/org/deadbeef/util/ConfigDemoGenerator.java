package org.deadbeef.util;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import lombok.SneakyThrows;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ConfigDemoGenerator {

  // https://bitbucket.org/snakeyaml/snakeyaml/wiki/Documentation

  @Test
  public void generateClient() {
    JsonObject object = new JsonObject();
    object.put("httpPort", 14483);
    object.put("httpsPort", 14484);
    object.put("remoteServer", "example.com");
    object.put("localPort", 14482);
    object.put("secretId", "a-secret-id");
    object.put("secretKey", "a-secret-key");
    object.put("preferNativeTransport", true);
    object.put("addressResolver", Arrays.asList("8.8.8.8", "114.114.114.114"));
    object.put("httpClient", new HttpClientOptions());
    object.put("netClient", new NetClientOptions());
    object.put("httpServer", new HttpServerOptions());
    printConfig(object);
  }

  @Test
  public void generateServer() {
    JsonObject object = new JsonObject();
    object.put("httpPort", 14483);
    object.put("httpsPort", 14484);
    object.put(
        "auth",
        List.of(
            Map.of("secretId", "one-secret-id", "secretKey", "one-secret-key"),
            Map.of("secretId", "another-secret-id", "secretKey", "another-secret-key")));
    object.put("preferNativeTransport", true);
    object.put("addressResolver", Arrays.asList("8.8.8.8", "114.114.114.114"));
    object.put("httpClient", new HttpClientOptions());
    object.put("httpServer", new HttpServerOptions());
    object.put("httpsClient", new NetClientOptions());
    object.put("httpsServer", new NetServerOptions());
    printConfig(object);
  }

  @SneakyThrows
  private void printConfig(JsonObject jsonObject) {
    YAMLMapper yamlMapper = new YAMLMapperFactory().get();
    // YAMLMapper yamlMapper = new YAMLMapper();
    yamlMapper.enable(SerializationFeature.INDENT_OUTPUT);
    String dump = yamlMapper.writeValueAsString(jsonObject);
    System.out.println(dump);
  }
}
