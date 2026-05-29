package org.deadbeef.route;

import static org.junit.Assert.assertEquals;

import io.vertx.core.net.SocketAddress;
import org.junit.Test;

public class AuthoritiesTest {

  @Test
  public void authorityWithExplicitPort() {
    SocketAddress address = Authorities.fromAuthority("example.com:8443", 443);
    assertEquals("example.com", address.host());
    assertEquals(8443, address.port());
  }

  @Test
  public void authorityAppliesDefaultPort() {
    SocketAddress address = Authorities.fromAuthority("example.com", 443);
    assertEquals("example.com", address.host());
    assertEquals(443, address.port());
  }

  @Test(expected = IllegalArgumentException.class)
  public void authorityRejectsEmpty() {
    Authorities.fromAuthority("", 443);
  }

  @Test
  public void absoluteUriExplicitPort() {
    SocketAddress address = Authorities.fromAbsoluteUri("http://example.com:8080/a?b=1");
    assertEquals("example.com", address.host());
    assertEquals(8080, address.port());
  }

  @Test
  public void absoluteUriHttpDefaults80() {
    SocketAddress address = Authorities.fromAbsoluteUri("http://example.com/path");
    assertEquals("example.com", address.host());
    assertEquals(80, address.port());
  }

  @Test
  public void absoluteUriHttpsDefaults443() {
    SocketAddress address = Authorities.fromAbsoluteUri("https://example.com/path");
    assertEquals("example.com", address.host());
    assertEquals(443, address.port());
  }

  @Test(expected = IllegalArgumentException.class)
  public void absoluteUriRejectsMissingHost() {
    Authorities.fromAbsoluteUri("/just/a/path");
  }
}
