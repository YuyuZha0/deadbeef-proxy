package org.deadbeef.route;

import com.google.common.io.CharStreams;
import com.google.common.net.InetAddresses;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

public sealed interface HostNameMatcher extends Predicate<String>, Closeable
    permits HostNameMatcherImpl, EmptyMatcher {

  static HostNameMatcher create(Iterable<String> hostNames) {
    if (hostNames == null
        || (hostNames instanceof Collection<?> collection && collection.isEmpty())) {
      return EmptyMatcher.INSTANCE;
    }
    List<String> filteredHostNames =
        StreamSupport.stream(hostNames.spliterator(), false)
            .map(StringUtils::trimToNull)
            .filter(StringUtils::isNotEmpty)
            .toList();
    if (filteredHostNames.isEmpty()) {
      return EmptyMatcher.INSTANCE;
    }
    return HostNameMatcherImpl.create(filteredHostNames);
  }

  static HostNameMatcher fromClasspathFile(@NonNull String classpathFile) {
    try (InputStream inputStream =
        HostNameMatcher.class.getClassLoader().getResourceAsStream(classpathFile)) {
      if (inputStream == null) {
        throw new IllegalArgumentException("Classpath resource not found: " + classpathFile);
      }
      try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
        List<String> hostNames =
            CharStreams.readLines(reader).stream()
                // drop '#' comments — whole-line or inline — so the lists can be documented
                // (host names and globs never contain '#'), then skip blanks
                .map(line -> StringUtils.substringBefore(line, "#"))
                .map(StringUtils::trimToEmpty)
                .filter(line -> !line.isEmpty())
                .toList();
        return create(hostNames);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to load host names from classpath file: " + classpathFile, e);
    }
  }

  default boolean match(String hostName) {
    if (InetAddresses.isInetAddress(hostName)) {
      return matchAddress(InetAddresses.forString(hostName));
    }
    return matchName(hostName);
  }

  /** Match a host name (non-IP) against the glob patterns. */
  boolean matchName(String hostName);

  /** Match an IP literal against the exact-IP set. */
  boolean matchAddress(InetAddress ipAddress);

  @Override
  default boolean test(String s) {
    return match(s);
  }

  default void close() {
    // Default no-op implementation; override if needed
  }
}
