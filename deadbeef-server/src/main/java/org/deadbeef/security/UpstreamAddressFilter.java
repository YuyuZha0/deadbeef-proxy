package org.deadbeef.security;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.util.function.Predicate;
import lombok.NonNull;

/**
 * Deny-list policy used by the server-side handlers to decide whether a resolved upstream address
 * is safe to dial. Intended to defeat SSRF: an authenticated client should not be able to use the
 * proxy as a relay into the server's private network, loopback, or cloud-metadata endpoints.
 *
 * <p>The input type matches what Vert.x's {@link io.vertx.core.impl.AddressResolver} returns — JDK
 * {@link InetAddress}. Classification is delegated entirely to the JDK predicates plus Guava's
 * {@link InetAddresses#isMaximum} for the IPv4 broadcast case.
 *
 * <p>Guava's {@code InetAddresses.forString} canonicalises IPv4-mapped IPv6 literals like {@code
 * ::ffff:127.0.0.1} into {@code Inet4Address}, so when callers parse literals through it the v4
 * predicates fire correctly.
 *
 * <p>Default policy (via {@link #defaultDenyList()}) rejects every JDK-recognized non-public
 * category. Use {@link #builder()} to relax individual categories for deployments that legitimately
 * need private-network reachability through the proxy.
 *
 * <p><b>Known gap:</b> IPv6 unique-local addresses (fc00::/7) have no JDK predicate and are not
 * denied here. Operators concerned about ULA should ensure their internal networks are unreachable
 * from the proxy server by other means (firewall / route table).
 */
public final class UpstreamAddressFilter implements Predicate<InetAddress> {

  private final boolean allowLoopback;
  private final boolean allowLinkLocal;
  private final boolean allowSiteLocal;
  private final boolean allowMulticast;

  private UpstreamAddressFilter(Builder b) {
    this.allowLoopback = b.allowLoopback;
    this.allowLinkLocal = b.allowLinkLocal;
    this.allowSiteLocal = b.allowSiteLocal;
    this.allowMulticast = b.allowMulticast;
  }

  /** Strict default: deny every JDK-recognized non-public category. */
  public static UpstreamAddressFilter defaultDenyList() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** True if {@code addr} is allowed to be dialed. */
  @Override
  public boolean test(InetAddress addr) {
    return denyReason(addr) == null;
  }

  /** Returns null if allowed, otherwise a short human-readable reason. */
  public String denyReason(@NonNull InetAddress addr) {
    if (addr.isAnyLocalAddress()) {
      return "any-local";
    }
    if (addr.isLoopbackAddress() && !allowLoopback) {
      return "loopback";
    }
    if (addr.isLinkLocalAddress() && !allowLinkLocal) {
      return "link-local";
    }
    if (addr.isSiteLocalAddress() && !allowSiteLocal) {
      return "site-local";
    }
    if (addr.isMulticastAddress() && !allowMulticast) {
      return "multicast";
    }
    if (InetAddresses.isMaximum(addr)) {
      return "broadcast";
    }
    return null;
  }

  public static final class Builder {
    private boolean allowLoopback = false;
    private boolean allowLinkLocal = false;
    private boolean allowSiteLocal = false;
    private boolean allowMulticast = false;

    public Builder allowLoopback() {
      this.allowLoopback = true;
      return this;
    }

    public Builder allowLinkLocal() {
      this.allowLinkLocal = true;
      return this;
    }

    public Builder allowSiteLocal() {
      this.allowSiteLocal = true;
      return this;
    }

    public Builder allowMulticast() {
      this.allowMulticast = true;
      return this;
    }

    public UpstreamAddressFilter build() {
      return new UpstreamAddressFilter(this);
    }
  }
}
