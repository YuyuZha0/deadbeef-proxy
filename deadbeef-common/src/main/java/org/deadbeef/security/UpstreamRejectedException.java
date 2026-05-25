package org.deadbeef.security;

import java.net.InetAddress;

/** Signals that an {@link UpstreamAddressFilter} denied the resolved upstream address. */
public final class UpstreamRejectedException extends RuntimeException {

  private final String host;
  private final InetAddress address;
  private final String reason;

  public UpstreamRejectedException(String host, InetAddress address, String reason) {
    super("Upstream " + host + " (" + address.getHostAddress() + ") rejected: " + reason);
    this.host = host;
    this.address = address;
    this.reason = reason;
  }

  public String host() {
    return host;
  }

  public InetAddress address() {
    return address;
  }

  public String reason() {
    return reason;
  }
}
