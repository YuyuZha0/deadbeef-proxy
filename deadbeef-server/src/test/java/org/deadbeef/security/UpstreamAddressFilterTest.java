package org.deadbeef.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import org.junit.Test;

public class UpstreamAddressFilterTest {

  private final UpstreamAddressFilter strict = UpstreamAddressFilter.defaultDenyList();

  private static InetAddress addr(String literal) {
    return InetAddresses.forString(literal);
  }

  // ---- default deny list rejects forbidden ranges ----

  @Test
  public void rejectsIpv4Loopback() {
    assertEquals("loopback", strict.denyReason(addr("127.0.0.1")));
    assertEquals("loopback", strict.denyReason(addr("127.255.255.254")));
  }

  @Test
  public void rejectsIpv6Loopback() {
    assertEquals("loopback", strict.denyReason(addr("::1")));
  }

  @Test
  public void rejectsIpv4LinkLocal() {
    // 169.254.169.254 is the AWS / GCP metadata endpoint
    assertEquals("link-local", strict.denyReason(addr("169.254.169.254")));
    assertEquals("link-local", strict.denyReason(addr("169.254.1.1")));
  }

  @Test
  public void rejectsIpv6LinkLocal() {
    assertEquals("link-local", strict.denyReason(addr("fe80::1")));
  }

  @Test
  public void rejectsRfc1918() {
    assertEquals("site-local", strict.denyReason(addr("10.0.0.1")));
    assertEquals("site-local", strict.denyReason(addr("172.16.0.1")));
    assertEquals("site-local", strict.denyReason(addr("192.168.1.1")));
  }

  @Test
  public void rejectsMulticast() {
    assertEquals("multicast", strict.denyReason(addr("224.0.0.1")));
    assertEquals("multicast", strict.denyReason(addr("239.255.255.255")));
    assertEquals("multicast", strict.denyReason(addr("ff02::1")));
  }

  @Test
  public void rejectsAnyLocal() {
    assertEquals("any-local", strict.denyReason(addr("0.0.0.0")));
    assertEquals("any-local", strict.denyReason(addr("::")));
  }

  @Test
  public void rejectsBroadcast() {
    assertEquals("broadcast", strict.denyReason(addr("255.255.255.255")));
  }

  // ---- public-routable addresses are accepted ----

  @Test
  public void acceptsPublicIpv4() {
    assertNull(strict.denyReason(addr("8.8.8.8")));
    assertNull(strict.denyReason(addr("1.1.1.1")));
    assertNull(strict.denyReason(addr("93.184.216.34"))); // example.com
  }

  @Test
  public void acceptsPublicIpv6() {
    assertNull(strict.denyReason(addr("2001:4860:4860::8888"))); // Google DNS
  }

  // ---- builder allows selectively relaxing categories ----

  @Test
  public void allowLoopbackOverridesDeny() {
    UpstreamAddressFilter f = UpstreamAddressFilter.builder().allowLoopback().build();
    assertTrue(f.test(addr("127.0.0.1")));
    assertTrue(f.test(addr("::1")));
    assertFalse(f.test(addr("10.0.0.1")));
    assertFalse(f.test(addr("169.254.169.254")));
  }

  @Test
  public void allowSiteLocalOverridesDeny() {
    UpstreamAddressFilter f = UpstreamAddressFilter.builder().allowSiteLocal().build();
    assertTrue(f.test(addr("10.0.0.1")));
    assertTrue(f.test(addr("192.168.1.1")));
    assertFalse(f.test(addr("127.0.0.1")));
  }

  @Test
  public void allowLinkLocalOverridesDeny() {
    UpstreamAddressFilter f = UpstreamAddressFilter.builder().allowLinkLocal().build();
    assertTrue(f.test(addr("169.254.169.254")));
    assertTrue(f.test(addr("fe80::1")));
  }

  @Test
  public void allowMulticastOverridesDeny() {
    UpstreamAddressFilter f = UpstreamAddressFilter.builder().allowMulticast().build();
    assertTrue(f.test(addr("224.0.0.1")));
    assertTrue(f.test(addr("ff02::1")));
  }

  // ---- test() vs denyReason() consistency ----

  @Test
  public void predicateAndReasonAgree() {
    String[] samples = {
      "127.0.0.1", "10.0.0.1", "169.254.169.254", "8.8.8.8", "224.0.0.1",
    };
    for (String s : samples) {
      InetAddress a = addr(s);
      boolean allowed = strict.test(a);
      String reason = strict.denyReason(a);
      if (allowed) {
        assertNull("expected null reason for allowed " + s, reason);
      } else {
        assertNotNull("expected non-null reason for denied " + s, reason);
      }
    }
  }

  // ---- IPv4-mapped IPv6 forms: Guava's forString canonicalises them, so the JDK v4 predicates
  // fire and the filter rejects them. Pinning that behaviour explicitly. ----

  @Test
  public void ipv4MappedLoopbackIsCaught() {
    assertEquals("loopback", strict.denyReason(addr("::ffff:127.0.0.1")));
  }

  @Test
  public void ipv4MappedLinkLocalIsCaught() {
    assertEquals("link-local", strict.denyReason(addr("::ffff:169.254.169.254")));
  }

  @Test
  public void ipv4MappedRfc1918IsCaught() {
    assertEquals("site-local", strict.denyReason(addr("::ffff:10.0.0.1")));
  }

  /**
   * Documented gap: IPv6 unique-local addresses (fc00::/7) have no JDK predicate, and the default
   * filter does not roll its own. Operators concerned about ULA should ensure their internal
   * networks are unreachable from the proxy server by other means (firewall / routing).
   */
  @Test
  public void documentedGap_uniqueLocalIpv6IsNotCaught() {
    assertNull(strict.denyReason(addr("fc00::1")));
    assertNull(strict.denyReason(addr("fd12:3456:789a::1")));
  }
}
