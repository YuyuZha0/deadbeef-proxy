package org.deadbeef.client;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import org.deadbeef.protocol.HttpProto;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class HttpServerRequestEncoderTest {

  private static HttpServerRequest mockRequest(
      HttpMethod method, HttpVersion version, String host, String path, String query) {
    HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    if (host != null) {
      headers.add(HttpHeaderNames.HOST, host);
    }
    Mockito.when(request.method()).thenReturn(method);
    Mockito.when(request.version()).thenReturn(version);
    Mockito.when(request.scheme()).thenReturn("http");
    Mockito.when(request.host()).thenReturn(host);
    Mockito.when(request.path()).thenReturn(path);
    Mockito.when(request.query()).thenReturn(query);
    Mockito.when(request.headers()).thenReturn(headers);
    Mockito.when(request.getHeader(HttpHeaderNames.HOST)).thenReturn(host);
    return request;
  }

  @Test
  public void encodesGetRequest() {
    HttpServerRequest request =
        mockRequest(HttpMethod.GET, HttpVersion.HTTP_1_1, "example.com", "/foo", null);

    HttpProto.Request encoded = new HttpServerRequestEncoder().apply(request);

    assertEquals(HttpProto.Method.GET, encoded.getMethod());
    assertEquals(HttpProto.Version.HTTP_1_1, encoded.getVersion());
    assertEquals("http", encoded.getScheme());
    assertEquals("http://example.com/foo", encoded.getAbsoluteUri());
  }

  @Test
  public void encodesPostWithPortAndQuery() {
    HttpServerRequest request =
        mockRequest(HttpMethod.POST, HttpVersion.HTTP_1_0, "api.test:8080", "/items", "limit=5");

    HttpProto.Request encoded = new HttpServerRequestEncoder().apply(request);

    assertEquals(HttpProto.Method.POST, encoded.getMethod());
    assertEquals(HttpProto.Version.HTTP_1_0, encoded.getVersion());
    assertEquals("http://api.test:8080/items?limit=5", encoded.getAbsoluteUri());
  }

  @Test
  public void mapsHttp2Version() {
    HttpServerRequest request =
        mockRequest(HttpMethod.PUT, HttpVersion.HTTP_2, "h2.test", "/", null);

    HttpProto.Request encoded = new HttpServerRequestEncoder().apply(request);

    assertEquals(HttpProto.Version.HTTP_2, encoded.getVersion());
  }

  @Test
  public void handlesPathWithoutLeadingSlash() {
    HttpServerRequest request =
        mockRequest(HttpMethod.GET, HttpVersion.HTTP_1_1, "example.com", "bar", null);

    HttpProto.Request encoded = new HttpServerRequestEncoder().apply(request);

    assertEquals("http://example.com/bar", encoded.getAbsoluteUri());
  }

  @Test
  public void handlesProxyProtocolPathThatRepeatsHost() {
    HttpServerRequest request =
        mockRequest(
            HttpMethod.GET,
            HttpVersion.HTTP_1_1,
            "example.com",
            "example.com/path",
            null);

    HttpProto.Request encoded = new HttpServerRequestEncoder().apply(request);

    assertEquals("http://example.com/path", encoded.getAbsoluteUri());
  }

  @Test
  public void passesThroughHeaders() {
    HttpServerRequest request =
        mockRequest(HttpMethod.GET, HttpVersion.HTTP_1_1, "example.com", "/", null);
    request.headers().add("Accept", "application/json");
    request.headers().add("X-Trace-Id", "abc");

    HttpProto.Request encoded = new HttpServerRequestEncoder().apply(request);

    assertEquals("application/json", encoded.getHeaders().getAccept());
    assertEquals("abc", encoded.getHeaders().getUndeclaredPairsMap().get("x-trace-id"));
  }
}
