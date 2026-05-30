package org.deadbeef.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

  @Test
  public void isLoopbackRecognisesLiteralsAndLocalhost() {
    assertTrue(Authorities.isLoopback("127.0.0.1"));
    assertTrue(Authorities.isLoopback("127.1.2.3")); // all of 127.0.0.0/8
    assertTrue(Authorities.isLoopback("::1"));
    assertTrue(Authorities.isLoopback("localhost"));
    assertTrue(Authorities.isLoopback("LOCALHOST")); // case-insensitive
  }

  @Test
  public void isLoopbackRejectsNonLoopback() {
    assertFalse(Authorities.isLoopback("example.com"));
    assertFalse(Authorities.isLoopback("8.8.8.8"));
    assertFalse(Authorities.isLoopback("192.168.1.1"));
    assertFalse(Authorities.isLoopback("")); // empty
    assertFalse(Authorities.isLoopback(null));
  }

  @Test
  public void isSelfTargetMatchesLoopbackOnLocalPort() {
    assertTrue(Authorities.isSelfTarget(SocketAddress.inetSocketAddress(8080, "127.0.0.1"), 8080));
    assertTrue(Authorities.isSelfTarget(SocketAddress.inetSocketAddress(8080, "localhost"), 8080));
    // wrong port -> a legitimate local service, not self
    assertFalse(Authorities.isSelfTarget(SocketAddress.inetSocketAddress(80, "127.0.0.1"), 8080));
    // right port but not loopback -> external host that merely shares the port number
    assertFalse(Authorities.isSelfTarget(SocketAddress.inetSocketAddress(8080, "8.8.8.8"), 8080));
  }
}
