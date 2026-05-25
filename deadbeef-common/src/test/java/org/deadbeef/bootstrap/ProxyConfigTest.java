package org.deadbeef.bootstrap;

import org.junit.Test;

public class ProxyConfigTest {

  @Test
  public void verifyPortAcceptsBoundary() {
    ProxyConfig.verifyPort(1, "p");
    ProxyConfig.verifyPort(65535, "p");
    ProxyConfig.verifyPort(8080, "p");
  }

  @Test(expected = IllegalArgumentException.class)
  public void verifyPortRejectsZero() {
    ProxyConfig.verifyPort(0, "p");
  }

  @Test(expected = IllegalArgumentException.class)
  public void verifyPortRejectsNegative() {
    ProxyConfig.verifyPort(-1, "p");
  }

  @Test(expected = IllegalArgumentException.class)
  public void verifyPortRejectsTooLarge() {
    ProxyConfig.verifyPort(65536, "p");
  }

  @Test
  public void verifyStringNotBlankAcceptsNonBlank() {
    ProxyConfig.verifyStringNotBlank("x", "f");
    ProxyConfig.verifyStringNotBlank("  x  ", "f");
  }

  @Test(expected = IllegalArgumentException.class)
  public void verifyStringNotBlankRejectsNull() {
    ProxyConfig.verifyStringNotBlank(null, "f");
  }

  @Test(expected = IllegalArgumentException.class)
  public void verifyStringNotBlankRejectsEmpty() {
    ProxyConfig.verifyStringNotBlank("", "f");
  }

  @Test(expected = IllegalArgumentException.class)
  public void verifyStringNotBlankRejectsWhitespace() {
    ProxyConfig.verifyStringNotBlank("   ", "f");
  }
}
