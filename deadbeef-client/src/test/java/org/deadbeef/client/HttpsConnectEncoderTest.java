package org.deadbeef.client;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.protocol.HttpProto;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpsConnectEncoderTest {

  private static HttpServerRequest mockRequest(String hostHeader) {
    HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
    MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    if (hostHeader != null) {
      headers.add(HttpHeaderNames.HOST, hostHeader);
    }
    Mockito.when(request.headers()).thenReturn(headers);
    Mockito.when(request.getHeader(HttpHeaderNames.HOST)).thenReturn(hostHeader);
    return request;
  }

  @Test
  public void parsesHostAndPort() {
    HttpsConnectEncoder encoder =
        new HttpsConnectEncoder(new ProxyAuthenticationGenerator("id", "key"));

    HttpProto.ConnectRequest encoded = encoder.apply(mockRequest("secure.example.com:8443"));

    assertEquals("secure.example.com", encoded.getHost());
    assertEquals(8443, encoded.getPort());
  }

  @Test
  public void fallsBackToDefaultPort443() {
    HttpsConnectEncoder encoder =
        new HttpsConnectEncoder(new ProxyAuthenticationGenerator("id", "key"));

    HttpProto.ConnectRequest encoded = encoder.apply(mockRequest("example.com"));

    assertEquals(443, encoded.getPort());
  }

  @Test
  public void populatesAuthFromInjectedGenerator() {
    ProxyAuthenticationGenerator generator = new ProxyAuthenticationGenerator("svc-1", "shh");
    HttpsConnectEncoder encoder = new HttpsConnectEncoder(generator);

    HttpProto.ConnectRequest encoded = encoder.apply(mockRequest("example.com:443"));

    assertTrue(encoded.hasAuth());
    assertEquals("svc-1", encoded.getAuth().getSecretId());
    assertTrue(encoded.getAuth().hasTimestamp());
  }

  @Test
  public void passesThroughHeaders() {
    HttpsConnectEncoder encoder =
        new HttpsConnectEncoder(new ProxyAuthenticationGenerator("id", "key"));
    HttpServerRequest request = mockRequest("example.com:443");
    request.headers().add("User-Agent", "test/1.0");

    HttpProto.ConnectRequest encoded = encoder.apply(request);

    assertEquals("test/1.0", encoded.getHeaders().getUserAgent());
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullGenerator() {
    new HttpsConnectEncoder(null);
  }
}
