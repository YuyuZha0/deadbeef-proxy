package org.deadbeef.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import org.deadbeef.protocol.HttpProto;
import org.deadbeef.util.HttpHeaderDecoder;
import org.junit.Test;
import org.mockito.Mockito;

public class HttpClientResponseEncoderTest {

  @Test
  public void encodesStatusAndMessage() {
    HttpClientResponse response = Mockito.mock(HttpClientResponse.class);
    Mockito.when(response.statusCode()).thenReturn(404);
    Mockito.when(response.statusMessage()).thenReturn("Not Found");
    Mockito.when(response.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

    HttpProto.Response encoded = new HttpClientResponseEncoder().apply(response);

    assertEquals(404, encoded.getStatusCode());
    assertEquals("Not Found", encoded.getStatusMessage());
  }

  @Test
  public void omitsStatusMessageWhenEmpty() {
    HttpClientResponse response = Mockito.mock(HttpClientResponse.class);
    Mockito.when(response.statusCode()).thenReturn(204);
    Mockito.when(response.statusMessage()).thenReturn("");
    Mockito.when(response.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

    HttpProto.Response encoded = new HttpClientResponseEncoder().apply(response);

    assertEquals(204, encoded.getStatusCode());
    assertFalse(encoded.hasStatusMessage());
  }

  @Test
  public void passesThroughHeaders() {
    HttpClientResponse response = Mockito.mock(HttpClientResponse.class);
    Mockito.when(response.statusCode()).thenReturn(200);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.add("Content-Type", "text/plain");
    headers.add("X-Trace-Id", "xyz");
    Mockito.when(response.headers()).thenReturn(headers);

    HttpProto.Response encoded = new HttpClientResponseEncoder().apply(response);

    assertEquals("text/plain", encoded.getHeaders().getContentType());
    assertEquals("xyz", encoded.getHeaders().getUndeclaredPairsMap().get("x-trace-id"));
  }

  @Test
  public void stripsHopByHopHeaders() {
    HttpClientResponse response = Mockito.mock(HttpClientResponse.class);
    Mockito.when(response.statusCode()).thenReturn(200);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    headers.add(HttpHeaderNames.CONNECTION, "close");
    headers.add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
    headers.add(HttpHeaderNames.KEEP_ALIVE, "timeout=5");
    headers.add(HttpHeaderNames.UPGRADE, "h2c");
    // End-to-end headers must survive.
    headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain");
    headers.add("X-Trace-Id", "xyz");
    Mockito.when(response.headers()).thenReturn(headers);

    HttpProto.Response encoded = new HttpClientResponseEncoder().apply(response);
    MultiMap decoded = new HttpHeaderDecoder().apply(encoded.getHeaders());

    assertNull(decoded.get(HttpHeaderNames.CONNECTION));
    assertNull(decoded.get(HttpHeaderNames.TRANSFER_ENCODING));
    assertNull(decoded.get(HttpHeaderNames.KEEP_ALIVE));
    assertNull(decoded.get(HttpHeaderNames.UPGRADE));
    assertEquals("text/plain", decoded.get(HttpHeaderNames.CONTENT_TYPE));
    assertEquals("xyz", decoded.get("X-Trace-Id"));
  }

  @Test
  public void toleratesNullHeaders() {
    HttpClientResponse response = Mockito.mock(HttpClientResponse.class);
    Mockito.when(response.statusCode()).thenReturn(200);
    Mockito.when(response.headers()).thenReturn(null);

    HttpProto.Response encoded = new HttpClientResponseEncoder().apply(response);

    assertEquals(200, encoded.getStatusCode());
    assertFalse(encoded.hasHeaders());
  }
}
