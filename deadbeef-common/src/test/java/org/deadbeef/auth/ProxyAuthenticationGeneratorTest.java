package org.deadbeef.auth;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.lang3.RandomStringUtils;
import org.deadbeef.protocol.HttpProto;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ProxyAuthenticationGeneratorTest {

  @Test
  public void producesPopulatedAuthFields() {
    ProxyAuthenticationGenerator generator =
        new ProxyAuthenticationGenerator(RandomStringUtils.randomAlphanumeric(16), "secret");

    HttpProto.ProxyAuthentication auth = generator.get();

    assertTrue(auth.hasSecretId());
    assertTrue(auth.hasTimestamp());
    assertTrue(auth.hasNonce());
    assertTrue(auth.hasSignature());
    assertFalse(auth.getSecretId().isEmpty());
    assertFalse(auth.getNonce().isEmpty());
    assertFalse(auth.getSignature().isEmpty());
  }

  @Test
  public void fixedClockYieldsFixedTimestamp() {
    long fixed = 1_700_000_000_000L;
    ProxyAuthenticationGenerator generator =
        new ProxyAuthenticationGenerator("id-1", "key-1", () -> fixed);

    assertEquals(fixed, generator.get().getTimestamp());
    assertEquals(fixed, generator.get().getTimestamp());
  }

  @Test
  public void nonceIsRandomized() {
    ProxyAuthenticationGenerator generator =
        new ProxyAuthenticationGenerator("id-1", "key-1");

    assertNotEquals(generator.get().getNonce(), generator.get().getNonce());
  }

  @Test
  public void getStringIsBase64UrlDecodableToProto() throws InvalidProtocolBufferException {
    ProxyAuthenticationGenerator generator =
        new ProxyAuthenticationGenerator("id-1", "key-1");

    String encoded = generator.getString();
    byte[] bytes = BaseEncoding.base64Url().decode(encoded);
    HttpProto.ProxyAuthentication decoded = HttpProto.ProxyAuthentication.parseFrom(bytes);

    assertEquals("id-1", decoded.getSecretId());
    assertTrue(decoded.hasTimestamp());
    assertFalse(decoded.getNonce().isEmpty());
    assertFalse(decoded.getSignature().isEmpty());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsEmptySecretId() {
    new ProxyAuthenticationGenerator("", "secret");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsEmptySecretKey() {
    new ProxyAuthenticationGenerator("id", "");
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullClock() {
    new ProxyAuthenticationGenerator("id", "key", null);
  }
}
