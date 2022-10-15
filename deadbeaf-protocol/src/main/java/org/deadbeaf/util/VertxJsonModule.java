package org.deadbeaf.util;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class VertxJsonModule extends SimpleModule {

  public VertxJsonModule() {
    addDeserializer(JsonObject.class, new JsonObjectDeserializer());
    addDeserializer(JsonArray.class, new JsonArrayDeserializer());
  }
}
