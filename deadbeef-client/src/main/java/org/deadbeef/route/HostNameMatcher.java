package org.deadbeef.route;

import com.google.common.io.CharStreams;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

public sealed interface HostNameMatcher extends Predicate<String>
    permits HostNameMatcherImpl, EmptyMatcher {

  static HostNameMatcher create(@NonNull Vertx vertx, Iterable<String> hostNames) {
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
    return HostNameMatcherImpl.create(vertx, filteredHostNames);
  }

  static HostNameMatcher fromClasspathFile(@NonNull Vertx vertx, @NonNull String classpathFile) {
    try (InputStream inputStream =
        HostNameMatcher.class.getClassLoader().getResourceAsStream(classpathFile)) {
      if (inputStream == null) {
        throw new IllegalArgumentException("Classpath resource not found: " + classpathFile);
      }
      try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
        List<String> hostNames =
            CharStreams.readLines(reader).stream()
                .map(StringUtils::trimToEmpty)
                // skip blank lines and '#' comments so the bundled lists can be grouped/documented
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        return create(vertx, hostNames);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to load host names from classpath file: " + classpathFile, e);
    }
  }

  boolean match(String hostName);

  @Override
  default boolean test(String s) {
    return match(s);
  }
}
