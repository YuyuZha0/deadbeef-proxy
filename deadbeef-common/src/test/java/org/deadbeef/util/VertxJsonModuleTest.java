package org.deadbeef.util;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import org.junit.Test;

public class VertxJsonModuleTest {

  private final YAMLMapper yamlMapper = new YAMLMapperFactory().get();

  @Test
  public void jsonObjectRoundTrip() throws Exception {
    JsonObject obj = new JsonObject().put("a", "1").put("b", 2).put("c", true);

    String yaml = yamlMapper.writeValueAsString(obj);
    JsonObject decoded = yamlMapper.readValue(yaml, JsonObject.class);

    assertEquals(obj, decoded);
  }

  @Test
  public void jsonArrayRoundTrip() throws Exception {
    JsonArray arr = new JsonArray().add("x").add(42).add(true);

    String yaml = yamlMapper.writeValueAsString(arr);
    JsonArray decoded = yamlMapper.readValue(yaml, JsonArray.class);

    assertEquals(arr, decoded);
  }

  @Test
  public void httpServerOptionsRoundTrip() throws Exception {
    HttpServerOptions options = new HttpServerOptions().setPort(8080).setReceiveBufferSize(65536);

    String yaml = yamlMapper.writeValueAsString(options);
    HttpServerOptions decoded = yamlMapper.readValue(yaml, HttpServerOptions.class);

    assertEquals(options.toJson(), decoded.toJson());
  }

  @Test
  public void netClientOptionsRoundTrip() throws Exception {
    NetClientOptions options =
        new NetClientOptions().setConnectTimeout(5_000).setReconnectAttempts(3);

    String yaml = yamlMapper.writeValueAsString(options);
    NetClientOptions decoded = yamlMapper.readValue(yaml, NetClientOptions.class);

    assertEquals(options.toJson(), decoded.toJson());
  }

  @Test
  public void netServerOptionsRoundTrip() throws Exception {
    NetServerOptions options = new NetServerOptions().setPort(12345).setAcceptBacklog(128);

    String yaml = yamlMapper.writeValueAsString(options);
    NetServerOptions decoded = yamlMapper.readValue(yaml, NetServerOptions.class);

    assertEquals(options.toJson(), decoded.toJson());
  }
}
