package org.deadbeef.route;

import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

/**
 * The production {@link RoutePolicy}: a single source of truth for the per-host routing decision.
 *
 * <p>Precedence:
 *
 * <ol>
 *   <li>An IP that is the remote proxy's own address, or any local-scope address (loopback /
 *       any-local / link-local / site-local (RFC1918) / multicast / broadcast), is always {@link
 *       Decision#DIRECT}: it cannot meaningfully traverse the remote proxy (which can't reach the
 *       client's network and SSRF-rejects such targets), and routing the proxy through itself would
 *       loop. This overrides {@code proxyAll}.
 *   <li>{@code proxyAll} forces everything else {@link Decision#REMOTE}.
 *   <li>A {@code localOnly} match wins over a {@code remoteOnly} match (a host in both lists is
 *       pinned {@link Decision#DIRECT}).
 *   <li>Anything unlisted is left to the adaptive {@link Decision#GATE} path.
 * </ol>
 */
public final class DefaultRoutePolicy implements RoutePolicy {

  private final HostNameMatcher localOnly;
  private final HostNameMatcher remoteOnly;
  private final boolean proxyAll;
  private final InetAddress remoteProxyAddress;

  public DefaultRoutePolicy(
      @NonNull HostNameMatcher localOnly,
      @NonNull HostNameMatcher remoteOnly,
      boolean proxyAll,
      String remoteProxyHost) {
    this.localOnly = localOnly;
    this.remoteOnly = remoteOnly;
    this.proxyAll = proxyAll;
    this.remoteProxyAddress =
        StringUtils.isNotEmpty(remoteProxyHost) && InetAddresses.isInetAddress(remoteProxyHost)
            ? InetAddresses.forString(remoteProxyHost)
            : null;
  }

  public DefaultRoutePolicy(HostNameMatcher localOnly, HostNameMatcher remoteOnly) {
    this(localOnly, remoteOnly, false, null);
  }

  @Override
  public Decision decide(String host) {
    if (InetAddresses.isInetAddress(host)) {
      InetAddress ipAddress = InetAddresses.forString(host);
      // Unroutable via the remote proxy -> always direct, even under proxyAll.
      if (ipAddress.equals(remoteProxyAddress)
          || ipAddress.isAnyLocalAddress()
          || ipAddress.isLoopbackAddress()
          || ipAddress.isLinkLocalAddress()
          || ipAddress.isSiteLocalAddress()
          || ipAddress.isMulticastAddress()
          || InetAddresses.isMaximum(ipAddress)) {
        return Decision.DIRECT;
      }
      if (proxyAll) {
        return Decision.REMOTE;
      }
      if (localOnly.matchAddress(ipAddress)) {
        return Decision.DIRECT;
      }
      return remoteOnly.matchAddress(ipAddress) ? Decision.REMOTE : Decision.GATE;
    }

    if (proxyAll) {
      return Decision.REMOTE;
    }
    if (localOnly.matchName(host)) {
      return Decision.DIRECT;
    }
    return remoteOnly.matchName(host) ? Decision.REMOTE : Decision.GATE;
  }
}
