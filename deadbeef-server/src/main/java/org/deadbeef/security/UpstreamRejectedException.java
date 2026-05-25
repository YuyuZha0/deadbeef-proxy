package org.deadbeef.security;

import java.net.InetAddress;

/** Signals that an {@link UpstreamAddressFilter} denied the resolved upstream address. */
public final class UpstreamRejectedException extends RuntimeException {

  private final String host;
  private final InetAddress address;
  private final String reason;

  public UpstreamRejectedException(String host, InetAddress address, String reason) {
    // Control-flow signal carried via Future.failedFuture; the stack trace would point at the
    // resolver every time and only add cost on every denied request.
    super(
        "Upstream " + host + " (" + address.getHostAddress() + ") rejected: " + reason,
        null,
        false,
        false);
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
