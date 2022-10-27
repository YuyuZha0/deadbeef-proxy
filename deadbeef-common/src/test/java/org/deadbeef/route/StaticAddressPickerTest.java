package org.deadbeef.route;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@PrepareForTest(HttpServerRequest.class)
@RunWith(PowerMockRunner.class)
public class StaticAddressPickerTest {

  @Test
  public void test() {
    HttpServerRequest request = PowerMockito.mock(HttpServerRequest.class);
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
}
