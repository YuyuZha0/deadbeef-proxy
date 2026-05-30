package org.deadbeef.route;

import static org.deadbeef.route.RoutePolicy.Decision.DIRECT;
import static org.deadbeef.route.RoutePolicy.Decision.GATE;
import static org.deadbeef.route.RoutePolicy.Decision.REMOTE;
import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

/** Exercises the real routing logic (independent of any handler). */
public class DefaultRoutePolicyTest {

  private RoutePolicy policy(List<String> local, List<String> remote) {
    return new DefaultRoutePolicy(HostNameMatcher.create(local), HostNameMatcher.create(remote));
  }

  // ---- host names ----

  @Test
  public void localOnlyNameIsDirect() {
    assertEquals(DIRECT, policy(List.of("*.cn.example"), List.of()).decide("a.cn.example"));
  }

  @Test
  public void remoteOnlyNameIsRemote() {
    assertEquals(REMOTE, policy(List.of(), List.of("*.blocked.com")).decide("www.blocked.com"));
  }

  @Test
  public void unlistedNameUsesGate() {
    assertEquals(GATE, policy(List.of("*.cn.example"), List.of("*.blocked.com")).decide("other.org"));
  }

  @Test
  public void localWinsWhenNameIsInBothLists() {
    assertEquals(DIRECT, policy(List.of("conflict.com"), List.of("conflict.com")).decide("conflict.com"));
  }

  // ---- IP literals ----

  @Test
  public void localScopeIpsAreAlwaysDirect() {
    RoutePolicy p = policy(List.of(), List.of()); // classification is intrinsic, not list-driven
    assertEquals(DIRECT, p.decide("127.0.0.1")); // loopback
    assertEquals(DIRECT, p.decide("::1")); // loopback v6
    assertEquals(DIRECT, p.decide("10.0.0.1")); // RFC1918
    assertEquals(DIRECT, p.decide("192.168.1.1")); // RFC1918
    assertEquals(DIRECT, p.decide("169.254.1.1")); // link-local
    assertEquals(DIRECT, p.decide("224.0.0.1")); // multicast
    assertEquals(DIRECT, p.decide("255.255.255.255")); // broadcast
  }

  @Test
  public void unlistedPublicIpUsesGate() {
    assertEquals(GATE, policy(List.of(), List.of()).decide("8.8.8.8"));
  }

  @Test
  public void publicIpRespectsTheLists() {
    assertEquals(REMOTE, policy(List.of(), List.of("8.8.8.8")).decide("8.8.8.8"));
    assertEquals(DIRECT, policy(List.of("8.8.8.8"), List.of()).decide("8.8.8.8"));
  }

  // ---- proxyAll ----

  @Test
  public void proxyAllForcesRemoteForRoutableHosts() {
    RoutePolicy p =
        new DefaultRoutePolicy(
            HostNameMatcher.create(List.of()), HostNameMatcher.create(List.of()), true, null);
    assertEquals(REMOTE, p.decide("example.com"));
    assertEquals(REMOTE, p.decide("8.8.8.8"));
  }

  @Test
  public void proxyAllStillRoutesLocalScopeIpsDirect() {
    // Local-scope addresses cannot traverse the remote proxy, so proxyAll must not force them remote.
    RoutePolicy p =
        new DefaultRoutePolicy(
            HostNameMatcher.create(List.of()), HostNameMatcher.create(List.of()), true, null);
    assertEquals(DIRECT, p.decide("127.0.0.1"));
    assertEquals(DIRECT, p.decide("192.168.1.1"));
  }

  // ---- remote proxy's own address ----

  @Test
  public void remoteProxyOwnAddressIsAlwaysDirect() {
    // The proxy's own IP must be reached directly even if it is also in remote_only (no self-loop).
    RoutePolicy p =
        new DefaultRoutePolicy(
            HostNameMatcher.create(List.of()), HostNameMatcher.create(List.of("9.9.9.9")), false,
            "9.9.9.9");
    assertEquals(DIRECT, p.decide("9.9.9.9"));
  }
}
