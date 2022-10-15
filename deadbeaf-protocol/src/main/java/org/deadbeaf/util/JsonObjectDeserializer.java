package org.deadbeaf.util;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class JsonObjectDeserializer extends StdDeserializer<JsonObject> {

  private final TypeReference<LinkedHashMap<String, Object>> typeReference =
      new TypeReference<LinkedHashMap<String, Object>>() {};

  public JsonObjectDeserializer() {
    super(JsonObject.class);
  }

  @Override
  public JsonObject deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JacksonException {
    Map<String, Object> map = jsonParser.readValueAs(typeReference);
    return new JsonObject(map);
  }
}
