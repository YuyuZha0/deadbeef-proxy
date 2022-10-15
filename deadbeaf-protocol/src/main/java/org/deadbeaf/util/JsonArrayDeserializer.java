package org.deadbeaf.util;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.vertx.core.json.JsonArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class JsonArrayDeserializer extends StdDeserializer<JsonArray> {

  private final TypeReference<ArrayList<Object>> typeReference =
      new TypeReference<ArrayList<Object>>() {};

  public JsonArrayDeserializer() {
    super(JsonArray.class);
  }

  @Override
  public JsonArray deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JacksonException {
    List<Object> list = jsonParser.readValueAs(typeReference);
    return new JsonArray(list);
  }
}
