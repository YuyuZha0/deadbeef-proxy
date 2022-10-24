package org.deadbeef.bootstrap;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public interface ProxyConfig {

  static void verifyPort(int port, String name) {
    Preconditions.checkArgument(
        port > 0 && port <= 65535, "Illegal port number for field `%s`: %s", name, port);
  }

  static void verifyStringNotBlank(String value, String name) {
    Preconditions.checkArgument(
        StringUtils.isNotBlank(value), "Field `%s` should not blank!", name);
  }

  Boolean getPreferNativeTransport();

  List<String> getAddressResolver();

  void verify();
}
