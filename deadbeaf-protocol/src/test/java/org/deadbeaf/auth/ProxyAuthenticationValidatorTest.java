package org.deadbeaf.auth;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ProxyAuthenticationValidatorTest {

  @Test
  public void test() {
    for (int i = 0; i < 10; ++i) {
      String secretId = RandomStringUtils.randomAlphabetic(16);
      String secretKey = RandomStringUtils.random(32);
      ProxyAuthenticationGenerator generator =
          new ProxyAuthenticationGenerator(secretId, secretKey);
      String auth = generator.get();
      System.out.println(auth + ":" + auth.length());
      ProxyAuthenticationValidator validator =
          ProxyAuthenticationValidator.simple(secretId, secretKey);
      assertTrue(validator.test(auth));
    }
  }
}