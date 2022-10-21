package org.deadbeef.protocol;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import org.junit.Test;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

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
    object.put("httpClient", new HttpClientOptions().toJson());
    object.put("netClient", new NetClientOptions().toJson());
    object.put("httpServer", new HttpServerOptions().toJson());
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
    object.put("httpClient", new HttpClientOptions().toJson());
    object.put("httpServer", new HttpServerOptions().toJson());
    object.put("httpsClient", new NetClientOptions().toJson());
    object.put("httpsServer", new NetServerOptions().toJson());
    printConfig(object);
  }

  private void printConfig(JsonObject jsonObject) {
    DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
    dumperOptions.setLineBreak(DumperOptions.LineBreak.UNIX);
    Yaml yaml = new Yaml(new JsonRepresenter(dumperOptions));
    String dump = yaml.dump(jsonObject);
    System.out.println(dump);
  }

  private static class JsonRepresenter extends Representer {

    public JsonRepresenter(DumperOptions dumperOptions) {
      super(dumperOptions);
      super.representers.put(JsonObject.class, new JsonObjectRepresent());
      super.representers.put(JsonArray.class, new JsonArrayRepresent());
    }

    final class JsonObjectRepresent implements Represent {
      @Override
      public Node representData(Object data) {
        JsonObject jsonObject = (JsonObject) data;
        return representMapping(Tag.MAP, jsonObject.getMap(), DumperOptions.FlowStyle.AUTO);
      }
    }

    final class JsonArrayRepresent implements Represent {
      @Override
      public Node representData(Object data) {
        JsonArray jsonArray = (JsonArray) data;
        return representSequence(Tag.SEQ, jsonArray.getList(), DumperOptions.FlowStyle.AUTO);
      }
    }
  }
}
