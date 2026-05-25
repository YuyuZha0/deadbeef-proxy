package org.deadbeef.route;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class HostHeaderAddressPickerTest {

  @Test
  public void hostPortFromHeader() {
    AddressPicker addressPicker = AddressPicker.ofHostHeader(443);

    HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
    Mockito.when(request.getHeader(HttpHeaderNames.HOST)).thenReturn("example.com:8080");

    SocketAddress address = addressPicker.apply(request);
    assertEquals(8080, address.port());
    assertEquals("example.com", address.host());
    assertEquals("example.com", address.hostName());
  }

  @Test
  public void ipv6FallsBackToDefaultPort() {
    AddressPicker addressPicker = AddressPicker.ofHostHeader(443);

    HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
    Mockito.when(request.getHeader(HttpHeaderNames.HOST))
        .thenReturn("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

    SocketAddress address = addressPicker.apply(request);
    assertEquals(443, address.port());
    assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", address.host());
    assertEquals("2001:db8:85a3:0:0:8a2e:370:7334", address.hostAddress());
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyHostHeaderRejected() {
    AddressPicker addressPicker = AddressPicker.ofHostHeader(443);

    HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
    Mockito.when(request.getHeader(HttpHeaderNames.HOST)).thenReturn("");

    addressPicker.apply(request);
  }
}
