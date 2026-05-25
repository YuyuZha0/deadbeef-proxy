package org.deadbeef.util;

import io.vertx.core.MultiMap;
import org.deadbeef.protocol.HttpProto;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpHeaderDecoderTest {

  @Test
  public void pascalCaseHyphenatedByDefault() {
    HttpProto.Headers headers =
        HttpProto.Headers.newBuilder()
            .setAccept("*/*")
            .setAcceptEncoding("gzip")
            .setContentType("application/json")
            .build();

    MultiMap multiMap = new HttpHeaderDecoder().apply(headers);

    assertEquals("*/*", multiMap.get("Accept"));
    assertEquals("gzip", multiMap.get("Accept-Encoding"));
    assertEquals("application/json", multiMap.get("Content-Type"));
  }

  @Test
  public void allLowerCaseMode() {
    HttpProto.Headers headers =
        HttpProto.Headers.newBuilder()
            .setAccept("*/*")
            .setAcceptEncoding("gzip")
            .build();

    MultiMap multiMap = new HttpHeaderDecoder(true).apply(headers);

    assertTrue(multiMap.names().contains("accept"));
    assertTrue(multiMap.names().contains("accept-encoding"));
    assertEquals("gzip", multiMap.get("accept-encoding"));
  }

  @Test
  public void undeclaredPairsPassThrough() {
    HttpProto.Headers headers =
        HttpProto.Headers.newBuilder()
            .setAccept("*/*")
            .putUndeclaredPairs("x-custom-header", "v1")
            .putUndeclaredPairs("x-another", "v2")
            .build();

    MultiMap multiMap = new HttpHeaderDecoder().apply(headers);

    assertEquals("*/*", multiMap.get("Accept"));
    assertEquals("v1", multiMap.get("x-custom-header"));
    assertEquals("v2", multiMap.get("x-another"));
  }

  @Test
  public void emptyHeaders() {
    MultiMap multiMap = new HttpHeaderDecoder().apply(HttpProto.Headers.getDefaultInstance());

    assertTrue(multiMap.isEmpty());
  }

  @Test
  public void visitDeliversToConsumer() {
    HttpProto.Headers headers =
        HttpProto.Headers.newBuilder()
            .setContentType("text/plain")
            .putUndeclaredPairs("x-trace-id", "abc123")
            .build();

    Map<String, String> collected = new TreeMap<>();
    new HttpHeaderDecoder().visit(headers, collected::put);

    assertEquals("text/plain", collected.get("Content-Type"));
    assertEquals("abc123", collected.get("x-trace-id"));
  }

  @Test
  public void unsetWellKnownFieldOmitted() {
    HttpProto.Headers headers =
        HttpProto.Headers.newBuilder().setAccept("*/*").build();

    Map<String, String> collected = new HashMap<>();
    new HttpHeaderDecoder().visit(headers, collected::put);

    assertEquals(1, collected.size());
    assertNull(collected.get("Accept-Encoding"));
  }
}
