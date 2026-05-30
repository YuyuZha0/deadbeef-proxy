package org.deadbeef.route;

import java.net.InetAddress;

final class EmptyMatcher implements HostNameMatcher {

  static final EmptyMatcher INSTANCE = new EmptyMatcher();

  @Override
  public boolean match(String hostName) {
    return false;
  }

  @Override
  public boolean matchName(String hostName) {
    return false;
  }

  @Override
  public boolean matchAddress(InetAddress ipAddress) {
    return false;
  }
}
