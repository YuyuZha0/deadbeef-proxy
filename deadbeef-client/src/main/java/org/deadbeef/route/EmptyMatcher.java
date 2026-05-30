package org.deadbeef.route;

final class EmptyMatcher implements HostNameMatcher {

  static final EmptyMatcher INSTANCE = new EmptyMatcher();

  @Override
  public boolean match(String hostName) {
    return false;
  }
}
