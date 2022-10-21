package org.deadbeef.auth;

import com.google.common.base.Preconditions;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.io.CharStreams;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.protocol.HttpProto;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@SuppressWarnings("UnstableApiUsage")
public final class ProxyAuthenticationGenerator implements Supplier<HttpProto.ProxyAuthentication> {

  private static final int NONCE_LEN = 16;
  private final String secretId;
  private final HashFunction sha256;

  public ProxyAuthenticationGenerator(String secretId, String secretKey) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(secretId), "empty secretId");
    Preconditions.checkArgument(StringUtils.isNotEmpty(secretKey), "empty secretKey");
    this.secretId = secretId;
    this.sha256 = Hashing.hmacSha256(secretKey.getBytes(StandardCharsets.UTF_8));
  }

  static byte[] signature(String secretId, long timestamp, byte[] nonce, HashFunction function) {
    return function
        .newHasher()
        .putString(secretId, StandardCharsets.UTF_8)
        .putLong(timestamp)
        .putBytes(nonce)
        .hash()
        .asBytes();
  }

  @Override
  public HttpProto.ProxyAuthentication get() {
    byte[] nonce = new byte[NONCE_LEN];
    ThreadLocalRandom.current().nextBytes(nonce);
    long timestamp = System.currentTimeMillis();
    byte[] signature = signature(secretId, timestamp, nonce, sha256);
    return HttpProto.ProxyAuthentication.newBuilder()
        .setSecretId(secretId)
        .setTimestamp(timestamp)
        .setNonce(ByteString.copyFrom(nonce))
        .setSignature(ByteString.copyFrom(signature))
        .build();
  }

  public String getString() {
    HttpProto.ProxyAuthentication proxyAuthentication = get();
    StringBuilder builder = new StringBuilder(128);
    try (Writer writer = CharStreams.asWriter(builder)) {
      try (OutputStream outputStream = BaseEncoding.base64Url().encodingStream(writer)) {
        proxyAuthentication.writeTo(outputStream);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return builder.toString();
  }
}
