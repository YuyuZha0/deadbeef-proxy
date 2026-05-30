package org.deadbeef.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.deadbeef.protocol.HttpProto;
import org.junit.Test;

public class ProxyAuthenticationValidatorTest {

  @Test
  public void validatesGeneratedAuth() {
    for (int i = 0; i < 5; ++i) {
      String secretId = RandomStringUtils.randomAlphabetic(16);
      String secretKey = RandomStringUtils.randomAlphanumeric(32);
      ProxyAuthenticationGenerator generator =
          new ProxyAuthenticationGenerator(secretId, secretKey);
      ProxyAuthenticationValidator validator =
          ProxyAuthenticationValidator.simple(secretId, secretKey);

      assertTrue(validator.test(generator.get()));
    }
  }

  @Test
  public void rejectsWrongSecretKey() {
    HttpProto.ProxyAuthentication auth = new ProxyAuthenticationGenerator("id", "real-key").get();
    assertFalse(ProxyAuthenticationValidator.simple("id", "fake-key").test(auth));
  }

  @Test
  public void rejectsUnknownSecretId() {
    HttpProto.ProxyAuthentication auth = new ProxyAuthenticationGenerator("id", "key").get();
    assertFalse(ProxyAuthenticationValidator.simple("other-id", "key").test(auth));
  }

  @Test
  public void rejectsTamperedSignature() {
    HttpProto.ProxyAuthentication original = new ProxyAuthenticationGenerator("id", "key").get();
    HttpProto.ProxyAuthentication tampered =
        original.toBuilder().setSignature(ByteString.copyFromUtf8("not-a-real-signature")).build();

    assertFalse(ProxyAuthenticationValidator.simple("id", "key").test(tampered));
  }

  @Test
  public void rejectsTamperedNonce() {
    HttpProto.ProxyAuthentication original = new ProxyAuthenticationGenerator("id", "key").get();
    HttpProto.ProxyAuthentication tampered =
        original.toBuilder().setNonce(ByteString.copyFromUtf8("0000000000000000")).build();

    assertFalse(ProxyAuthenticationValidator.simple("id", "key").test(tampered));
  }

  @Test
  public void rejectsTimestampOutsideWindow() {
    long oldTimestamp = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20);
    HttpProto.ProxyAuthentication auth =
        new ProxyAuthenticationGenerator("id", "key", () -> oldTimestamp).get();

    assertFalse(ProxyAuthenticationValidator.simple("id", "key").test(auth));
  }

  @Test
  public void rejectsFutureTimestampOutsideWindow() {
    long futureTimestamp = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(20);
    HttpProto.ProxyAuthentication auth =
        new ProxyAuthenticationGenerator("id", "key", () -> futureTimestamp).get();

    assertFalse(ProxyAuthenticationValidator.simple("id", "key").test(auth));
  }

  @Test
  public void rejectsNullAuth() {
    assertFalse(ProxyAuthenticationValidator.simple("id", "key").test(null));
  }

  @Test
  public void rejectsAuthMissingFields() {
    HttpProto.ProxyAuthentication blank = HttpProto.ProxyAuthentication.newBuilder().build();
    assertFalse(ProxyAuthenticationValidator.simple("id", "key").test(blank));
  }

  @Test
  public void testStringReturnsFalseForEmpty() {
    assertFalse(ProxyAuthenticationValidator.simple("id", "key").testString(""));
    assertFalse(ProxyAuthenticationValidator.simple("id", "key").testString(null));
  }

  @Test
  public void testStringReturnsFalseForInvalidBase64() {
    assertFalse(ProxyAuthenticationValidator.simple("id", "key").testString("!!!not_base64!!!"));
  }

  @Test
  public void testStringReturnsFalseForMalformedProto() {
    String junk = com.google.common.io.BaseEncoding.base64Url().encode("not-a-protobuf".getBytes());
    assertFalse(ProxyAuthenticationValidator.simple("id", "key").testString(junk));
  }

  @Test
  public void testStringRoundTripsGeneratedString() {
    ProxyAuthenticationGenerator gen = new ProxyAuthenticationGenerator("id", "key");
    ProxyAuthenticationValidator validator = ProxyAuthenticationValidator.simple("id", "key");

    assertTrue(validator.testString(gen.getString()));
  }

  @Test
  public void fromMapAcceptsMultipleSecrets() {
    ProxyAuthenticationValidator validator =
        ProxyAuthenticationValidator.fromMap(ImmutableMap.of("id-1", "key-1", "id-2", "key-2"));

    assertTrue(validator.test(new ProxyAuthenticationGenerator("id-1", "key-1").get()));
    assertTrue(validator.test(new ProxyAuthenticationGenerator("id-2", "key-2").get()));
    assertFalse(validator.test(new ProxyAuthenticationGenerator("id-1", "key-2").get()));
  }

  @Test
  public void fromEntriesAcceptsMultipleSecretsForSameId() {
    ProxyAuthenticationValidator validator =
        ProxyAuthenticationValidator.fromEntries(
            java.util.Arrays.asList(
                new java.util.AbstractMap.SimpleImmutableEntry<>("id", "key-a"),
                new java.util.AbstractMap.SimpleImmutableEntry<>("id", "key-b")));

    assertTrue(validator.test(new ProxyAuthenticationGenerator("id", "key-a").get()));
    assertTrue(validator.test(new ProxyAuthenticationGenerator("id", "key-b").get()));
    assertFalse(validator.test(new ProxyAuthenticationGenerator("id", "key-c").get()));
  }

  @Test
  public void simpleRejectsEmptySecretId() {
    try {
      ProxyAuthenticationValidator.simple("", "key");
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertEquals("empty secretId!", expected.getMessage());
    }
  }

  @Test
  public void simpleRejectsEmptySecretKeyAndReportsSecretId() {
    // Pins Bug A fix: error message should reference the secretId, not the secretKey.
    try {
      ProxyAuthenticationValidator.simple("my-id", "");
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertEquals("empty secretKey for secretId: my-id", expected.getMessage());
    }
  }

  @Test
  public void fromEntriesRejectsEmptySecretKeyAndReportsSecretId() {
    // Pins Bug B fix: subject/predicate of the message must match what was checked.
    try {
      ProxyAuthenticationValidator.fromMap(ImmutableMap.of("my-id", ""));
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertEquals("empty secretKey for secretId: my-id", expected.getMessage());
    }
  }

  // ---- replay protection ----

  @Test
  public void rejectsReplayedAuthPayload() {
    ProxyAuthenticationGenerator gen = new ProxyAuthenticationGenerator("id", "key");
    ProxyAuthenticationValidator validator = ProxyAuthenticationValidator.simple("id", "key");
    HttpProto.ProxyAuthentication auth = gen.get();

    assertTrue("first use should be accepted", validator.test(auth));
    assertFalse("second use of same nonce should be rejected", validator.test(auth));
    assertFalse("third use also rejected", validator.test(auth));
  }

  @Test
  public void rejectsReplayedAuthString() {
    ProxyAuthenticationGenerator gen = new ProxyAuthenticationGenerator("id", "key");
    ProxyAuthenticationValidator validator = ProxyAuthenticationValidator.simple("id", "key");
    String token = gen.getString();

    assertTrue(validator.testString(token));
    assertFalse(validator.testString(token));
  }

  @Test
  public void distinctNoncesUnderSameSecretAllAccepted() {
    ProxyAuthenticationGenerator gen = new ProxyAuthenticationGenerator("id", "key");
    ProxyAuthenticationValidator validator = ProxyAuthenticationValidator.simple("id", "key");

    for (int i = 0; i < 20; i++) {
      // gen.get() produces a fresh nonce each time
      assertTrue("call " + i, validator.test(gen.get()));
    }
  }

  @Test
  public void nonceCacheScopedPerSecretId() {
    // Forge two auth payloads sharing the same nonce + timestamp but under different
    // secretIds. The cache must key on (secretId, nonce); accepting both proves scoping.
    byte[] nonce = new byte[16];
    java.util.Arrays.fill(nonce, (byte) 0xAB);
    long ts = System.currentTimeMillis();

    HashFunction hf1 = Hashing.hmacSha256("key-1".getBytes(StandardCharsets.UTF_8));
    HashFunction hf2 = Hashing.hmacSha256("key-2".getBytes(StandardCharsets.UTF_8));

    HttpProto.ProxyAuthentication auth1 =
        HttpProto.ProxyAuthentication.newBuilder()
            .setSecretId("id-1")
            .setTimestamp(ts)
            .setNonce(ByteString.copyFrom(nonce))
            .setSignature(
                ByteString.copyFrom(ProxyAuthenticationGenerator.signature("id-1", ts, nonce, hf1)))
            .build();
    HttpProto.ProxyAuthentication auth2 =
        HttpProto.ProxyAuthentication.newBuilder()
            .setSecretId("id-2")
            .setTimestamp(ts)
            .setNonce(ByteString.copyFrom(nonce))
            .setSignature(
                ByteString.copyFrom(ProxyAuthenticationGenerator.signature("id-2", ts, nonce, hf2)))
            .build();

    ProxyAuthenticationValidator validator =
        ProxyAuthenticationValidator.fromMap(ImmutableMap.of("id-1", "key-1", "id-2", "key-2"));

    assertTrue(validator.test(auth1));
    assertTrue(validator.test(auth2));
    // But replaying either is still blocked.
    assertFalse(validator.test(auth1));
    assertFalse(validator.test(auth2));
  }

  @Test
  public void nonceCacheIsPerValidatorInstance() {
    // Two independent validators must have independent caches.
    HttpProto.ProxyAuthentication auth = new ProxyAuthenticationGenerator("id", "key").get();

    assertTrue(ProxyAuthenticationValidator.simple("id", "key").test(auth));
    // Same auth, fresh validator → should still be accepted.
    assertTrue(ProxyAuthenticationValidator.simple("id", "key").test(auth));
  }
}
