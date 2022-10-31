package org.deadbeef.util;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class YAMLMapperFactoryTest {

  private final YAMLMapper yamlMapper = new YAMLMapperFactory().get();

  @Test
  public void testArray() throws Exception {
    String json =
        "[\n"
            + "    {\n"
            + "      \"name\": \"Tom Cruise\",\n"
            + "      \"age\": 56,\n"
            + "      \"Born At\": \"Syracuse, NY\",\n"
            + "      \"Birthdate\": \"July 3, 1962\",\n"
            + "      \"photo\": \"https://jsonformatter.org/img/tom-cruise.jpg\"\n"
            + "    },\n"
            + "    {\n"
            + "      \"name\": \"Robert Downey Jr.\",\n"
            + "      \"age\": 53,\n"
            + "      \"Born At\": \"New York City, NY\",\n"
            + "      \"Birthdate\": \"April 4, 1965\",\n"
            + "      \"photo\": \"https://jsonformatter.org/img/Robert-Downey-Jr.jpg\"\n"
            + "    }\n"
            + "  ]";
    JsonArray array = new JsonArray(json);
    String s = yamlMapper.enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(array);
    System.out.println(s);
    assertEquals(array, yamlMapper.readValue(s, JsonArray.class));
  }

  @Test
  public void testObject() throws Exception {
    String json =
        "{\n"
            + "    \"glossary\": {\n"
            + "        \"title\": \"example glossary\",\n"
            + "\t\t\"GlossDiv\": {\n"
            + "            \"title\": \"S\",\n"
            + "\t\t\t\"GlossList\": {\n"
            + "                \"GlossEntry\": {\n"
            + "                    \"ID\": \"SGML\",\n"
            + "\t\t\t\t\t\"SortAs\": \"SGML\",\n"
            + "\t\t\t\t\t\"GlossTerm\": \"Standard Generalized Markup Language\",\n"
            + "\t\t\t\t\t\"Acronym\": \"SGML\",\n"
            + "\t\t\t\t\t\"Abbrev\": \"ISO 8879:1986\",\n"
            + "\t\t\t\t\t\"GlossDef\": {\n"
            + "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\",\n"
            + "\t\t\t\t\t\t\"GlossSeeAlso\": [\"GML\", \"XML\"]\n"
            + "                    },\n"
            + "\t\t\t\t\t\"GlossSee\": \"markup\"\n"
            + "                }\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "}";
    JsonObject object = new JsonObject(json);
    String s = yamlMapper.enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(object);
    System.out.println(s);
    assertEquals(object, yamlMapper.readValue(s, JsonObject.class));
  }

  @Test
  public void testHttpClientOptions() throws Exception {
    HttpClientOptions options = new HttpClientOptions();
    String s = yamlMapper.enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(options);
    System.out.println(s);
    assertEquals(options.toJson(), yamlMapper.readValue(s, HttpClientOptions.class).toJson());
  }
}
