package org.deadbeef.route;

import static org.junit.Assert.assertEquals;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import org.junit.Test;
import org.mockito.Mockito;

public class AuthorityOriginProviderTest {

  private static HttpServerRequest request(HttpMethod method, String uri, String hostHeader) {
    HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
    Mockito.when(request.method()).thenReturn(method);
    Mockito.when(request.uri()).thenReturn(uri);
    Mockito.when(request.getHeader(HttpHeaderNames.HOST)).thenReturn(hostHeader);
    return request;
  }

  @Test
  public void connectUsesAuthorityRequestTarget() {
    OriginProvider provider = OriginProvider.ofAuthority(443);
    SocketAddress address =
        provider.apply(request(HttpMethod.CONNECT, "example.com:8443", "ignored.example.com"));
    assertEquals("example.com", address.host());
    assertEquals(8443, address.port());
  }

  @Test
  public void connectAppliesDefaultPort() {
    OriginProvider provider = OriginProvider.ofAuthority(443);
    SocketAddress address = provider.apply(request(HttpMethod.CONNECT, "example.com", null));
    assertEquals("example.com", address.host());
    assertEquals(443, address.port());
  }

  @Test
  public void httpUsesAbsoluteUriAndExplicitPort() {
    OriginProvider provider = OriginProvider.ofAuthority(80);
    SocketAddress address =
        provider.apply(request(HttpMethod.GET, "http://example.com:8080/path?q=1", "example.com"));
    assertEquals("example.com", address.host());
    assertEquals(8080, address.port());
  }

  @Test
  public void httpsAbsoluteUriDefaultsTo443() {
    OriginProvider provider = OriginProvider.ofAuthority(80);
    SocketAddress address =
        provider.apply(request(HttpMethod.GET, "https://secure.example.com/path", null));
    assertEquals("secure.example.com", address.host());
    assertEquals(443, address.port());
  }

  @Test
  public void originFormFallsBackToHostHeader() {
    OriginProvider provider = OriginProvider.ofAuthority(80);
    SocketAddress address = provider.apply(request(HttpMethod.GET, "/path", "example.com:8080"));
    assertEquals("example.com", address.host());
    assertEquals(8080, address.port());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsWhenNoAuthorityAvailable() {
    OriginProvider provider = OriginProvider.ofAuthority(80);
    provider.apply(request(HttpMethod.GET, "/path", ""));
  }
}
