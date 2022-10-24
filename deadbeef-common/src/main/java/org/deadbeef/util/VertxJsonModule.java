package org.deadbeef.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.TCPSSLOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class VertxJsonModule extends SimpleModule {

  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
      new TypeReference<LinkedHashMap<String, Object>>() {};
  private static final TypeReference<ArrayList<Object>> LIST_TYPE =
      new TypeReference<ArrayList<Object>>() {};

  public VertxJsonModule() {
    addSerializer(JsonObject.class, new JsonObjectSerializer());
    addSerializer(JsonArray.class, new JsonArraySerializer());
    addSerializer(
        HttpClientOptions.class,
        new TCPSSLOptionsSerializer<>(HttpClientOptions.class, HttpClientOptions::toJson));
    addSerializer(
        HttpServerOptions.class,
        new TCPSSLOptionsSerializer<>(HttpServerOptions.class, HttpServerOptions::toJson));
    addSerializer(
        NetClientOptions.class,
        new TCPSSLOptionsSerializer<>(NetClientOptions.class, NetClientOptions::toJson));
    addSerializer(
        NetServerOptions.class,
        new TCPSSLOptionsSerializer<>(NetServerOptions.class, NetServerOptions::toJson));

    addDeserializer(JsonObject.class, new JsonObjectDeserializer());
    addDeserializer(JsonArray.class, new JsonArrayDeserializer());
    addDeserializer(
        HttpClientOptions.class,
        new TCPSSLOptionsDeserializer<>(HttpClientOptions.class, HttpClientOptions::new));
    addDeserializer(
        HttpServerOptions.class,
        new TCPSSLOptionsDeserializer<>(HttpServerOptions.class, HttpServerOptions::new));
    addDeserializer(
        NetClientOptions.class,
        new TCPSSLOptionsDeserializer<>(NetClientOptions.class, NetClientOptions::new));
    addDeserializer(
        NetServerOptions.class,
        new TCPSSLOptionsDeserializer<>(NetServerOptions.class, NetServerOptions::new));
  }

  static final class JsonObjectDeserializer extends StdDeserializer<JsonObject> {

    public JsonObjectDeserializer() {
      super(JsonObject.class);
    }

    @Override
    public JsonObject deserialize(
        JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      Map<String, Object> map = jsonParser.readValueAs(MAP_TYPE);
      return new JsonObject(map);
    }
  }

  static final class JsonArrayDeserializer extends StdDeserializer<JsonArray> {

    public JsonArrayDeserializer() {
      super(JsonArray.class);
    }

    @Override
    public JsonArray deserialize(
        JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      List<String> list = jsonParser.readValueAs(LIST_TYPE);
      return new JsonArray(list);
    }
  }

  static final class JsonObjectSerializer extends StdSerializer<JsonObject> {

    public JsonObjectSerializer() {
      super(JsonObject.class);
    }

    @Override
    public void serialize(
        JsonObject jsonObject, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
        throws IOException {
      jsonGenerator.writeObject(jsonObject.getMap());
    }
  }

  static final class JsonArraySerializer extends StdSerializer<JsonArray> {
    public JsonArraySerializer() {
      super(JsonArray.class);
    }

    @Override
    public void serialize(
        JsonArray jsonArray, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
        throws IOException {
      jsonGenerator.writeObject(jsonArray.getList());
    }
  }

  static final class TCPSSLOptionsDeserializer<T extends TCPSSLOptions> extends StdDeserializer<T> {

    private final Function<? super JsonObject, ? extends T> constructor;

    public TCPSSLOptionsDeserializer(
        Class<T> type, Function<? super JsonObject, ? extends T> constructor) {
      super(type);
      this.constructor = constructor;
    }

    @Override
    public T deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
        throws IOException {
      Map<String, Object> map = jsonParser.readValueAs(MAP_TYPE);
      return constructor.apply(new JsonObject(map));
    }
  }

  static final class TCPSSLOptionsSerializer<T extends TCPSSLOptions> extends StdSerializer<T> {

    private final Function<? super T, ? extends JsonObject> dumper;

    public TCPSSLOptionsSerializer(
        Class<T> type, Function<? super T, ? extends JsonObject> dumper) {
      super(type);
      this.dumper = dumper;
    }

    @Override
    public void serialize(T t, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
        throws IOException {
      JsonObject object = dumper.apply(t);
      jsonGenerator.writeObject(object.getMap());
    }
  }
}
