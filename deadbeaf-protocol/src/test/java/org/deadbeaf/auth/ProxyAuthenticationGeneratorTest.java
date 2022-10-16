package org.deadbeaf.auth;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

public class ProxyAuthenticationGeneratorTest {

  @Test
  public void test() {
    ProxyAuthenticationGenerator generator = new ProxyAuthenticationGenerator(RandomStringUtils.random(16), "bbb");

    System.out.println(generator.get().length());
  }
}
