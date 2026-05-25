package org.deadbeef.route;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StaticAddressPickerTest {

  @Test
  public void test() {
    HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
    AddressPicker addressPicker = AddressPicker.ofStatic(8080, "example.com");

    SocketAddress address = addressPicker.apply(request);
    assertEquals(8080, address.port());
    assertEquals("example.com", address.host());
    assertNull(address.hostAddress());

    AddressPicker addressPicker1 = AddressPicker.ofStatic(8891, "127.0.0.1");
    SocketAddress address1 = addressPicker1.apply(request);
    assertEquals(8891, address1.port());
    assertEquals("127.0.0.1", address1.host());
    assertEquals("127.0.0.1", address1.hostAddress());
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullAddress() {
    new StaticAddressPicker(null);
  }
}
