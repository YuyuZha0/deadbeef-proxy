package org.deadbeef.util;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClientOptions;
import java.util.Arrays;
import lombok.SneakyThrows;
import org.junit.Test;

public class ConfigDemoGenerator {

  @Test
  public void generateClient() {
    JsonObject object = new JsonObject();
    object.put("remotePort", 14483);
    object.put("remoteHost", "example.com");
    object.put("localPort", 14482);
    object.put("secretId", "a-secret-id");
    object.put("secretKey", "a-secret-key");
    object.put("preferNativeTransport", true);
    object.put("addressResolver", Arrays.asList("8.8.8.8", "114.114.114.114"));
    object.put("httpClient", new HttpClientOptions());
    object.put("localServer", new HttpServerOptions());
    printConfig(object);
  }

  @Test
  public void generateServer() {
    JsonObject object = new JsonObject();
    object.put("port", 14483);
    object.put(
        "auth",
        Lists.newArrayList(
            ImmutableMap.of("secretId", "one-secret-id", "secretKey", "one-secret-key"),
            ImmutableMap.of("secretId", "another-secret-id", "secretKey", "another-secret-key")));
    object.put("preferNativeTransport", true);
    object.put("addressResolver", Arrays.asList("8.8.8.8", "114.114.114.114"));
    object.put("httpClient", new HttpClientOptions());
    object.put("httpServer", new HttpServerOptions());
    object.put("netClient", new NetClientOptions());
    printConfig(object);
  }

  @SneakyThrows
  private void printConfig(JsonObject jsonObject) {
    YAMLMapper yamlMapper = new YAMLMapperFactory().get();
    yamlMapper.enable(SerializationFeature.INDENT_OUTPUT);
    String dump = yamlMapper.writeValueAsString(jsonObject);
    System.out.println(dump);
  }
}
