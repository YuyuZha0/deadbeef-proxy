package org.deadbeef.auth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.protocol.HttpProto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class ProxyAuthenticationValidator
    implements Predicate<HttpProto.ProxyAuthentication> {

  private static final long MAX_TIME_DELTA = TimeUnit.MINUTES.toMillis(15);
  private final ListMultimap<String, HashFunction> storedMap;

  private ProxyAuthenticationValidator(ListMultimap<String, HashFunction> storedMap) {
    this.storedMap = storedMap;
  }

  public static ProxyAuthenticationValidator simple(String secretId, String secretKey) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(secretId), "empty secretId!");
    Preconditions.checkArgument(
        StringUtils.isNotEmpty(secretKey), "empty secretKey for secretId: %s", secretKey);
    return new ProxyAuthenticationValidator(
        ImmutableListMultimap.of(
            secretId, Hashing.hmacSha256(secretKey.getBytes(StandardCharsets.UTF_8))));
  }

  public static ProxyAuthenticationValidator fromMap(@NonNull Map<String, String> map) {
    return fromEntries(map.entrySet());
  }

  public static ProxyAuthenticationValidator fromEntries(
      @NonNull Iterable<? extends Map.Entry<String, String>> iterable) {
    ImmutableListMultimap.Builder<String, HashFunction> builder = ImmutableListMultimap.builder();
    for (Map.Entry<String, String> entry : iterable) {
      Preconditions.checkArgument(StringUtils.isNotEmpty(entry.getKey()), "empty secretId!");
      Preconditions.checkArgument(
          StringUtils.isNotEmpty(entry.getValue()),
          "empty secretId for secretKey: %s",
          entry.getKey());
      builder.put(
          entry.getKey(), Hashing.hmacSha256(entry.getValue().getBytes(StandardCharsets.UTF_8)));
    }
    return new ProxyAuthenticationValidator(builder.build());
  }

  public boolean testString(String input) {
    if (StringUtils.isEmpty(input) || !BaseEncoding.base64Url().canDecode(input)) {
      return false;
    }
    byte[] bytes = BaseEncoding.base64Url().decode(input);
    HttpProto.ProxyAuthentication proxyAuthentication;
    try {
      proxyAuthentication = HttpProto.ProxyAuthentication.parseFrom(bytes);
    } catch (InvalidProtocolBufferException ignore) {
      return false;
    }

    return test(proxyAuthentication);
  }

  @Override
  public boolean test(HttpProto.ProxyAuthentication proxyAuthentication) {
    if (proxyAuthentication == null) {
      return false;
    }
    long timestamp;
    if (!proxyAuthentication.hasTimestamp()
        || Math.abs((timestamp = proxyAuthentication.getTimestamp()) - System.currentTimeMillis())
            > MAX_TIME_DELTA) {
      return false;
    }

    String secretId;
    if (!proxyAuthentication.hasSecretId()
        || StringUtils.isEmpty(secretId = proxyAuthentication.getSecretId())) {
      return false;
    }
    if (!proxyAuthentication.hasNonce() || proxyAuthentication.getNonce().isEmpty()) {
      return false;
    }
    byte[] nonce = proxyAuthentication.getNonce().toByteArray();
    if (!proxyAuthentication.hasSignature() || proxyAuthentication.getSignature().isEmpty()) {
      return false;
    }
    byte[] signature = proxyAuthentication.getSignature().toByteArray();
    List<HashFunction> hashFunctions = storedMap.get(secretId);
    if (hashFunctions.isEmpty()) {
      return false;
    }
    for (HashFunction function : hashFunctions) {
      if (Arrays.equals(
          signature,
          ProxyAuthenticationGenerator.signature(secretId, timestamp, nonce, function))) {
        return true;
      }
    }
    return false;
  }
}
