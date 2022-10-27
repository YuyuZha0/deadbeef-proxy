package org.deadbeef.route;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;

@PrepareForTest(HttpServerRequest.class)
@RunWith(PowerMockRunner.class)
public class HostHeaderAddressPickerTest {

  @Test
  public void test() {

    AddressPicker addressPicker = AddressPicker.ofHostHeader(443);

    HttpServerRequest request = PowerMockito.mock(HttpServerRequest.class);
    Mockito.when(request.getHeader(HttpHeaderNames.HOST)).thenReturn("example.com:8080");

    SocketAddress address = addressPicker.apply(request);
    assertEquals(8080, address.port());
    assertEquals("example.com", address.host());
    assertEquals("example.com", address.hostName());

    Mockito.when(request.getHeader(HttpHeaderNames.HOST))
        .thenReturn("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

    SocketAddress address1 = addressPicker.apply(request);
    assertEquals(443, address1.port());
    assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", address1.host());
    assertEquals("2001:db8:85a3:0:0:8a2e:370:7334", address1.hostAddress());
  }
}
