package org.deadbeaf.util;

import com.google.common.base.Preconditions;
import io.netty.util.concurrent.FastThreadLocal;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public final class Utils {

  // The yaml instance is not thread-safe!
  private static final FastThreadLocal<Yaml> YAML_FAST_THREAD_LOCAL =
      new FastThreadLocal<Yaml>() {
        @Override
        protected Yaml initialValue() {
          return new Yaml();
        }
      };

  private Utils() {
    throw new IllegalStateException();
  }

  public static <T> Handler<T> atMostOnce(Handler<? super T> original) {
    return new AtMostOnceHandler<>(original);
  }

  public static void exchangeCloseHook(@NonNull NetSocket alice, @NonNull NetSocket bob) {
    Preconditions.checkArgument(alice != bob);
    alice.closeHandler(
        Utils.atMostOnce(
            v -> {
              bob.closeHandler(null);
              bob.close();
            }));
    bob.closeHandler(
        Utils.atMostOnce(
            v -> {
              alice.closeHandler(null);
              alice.close();
            }));
  }

  @SuppressWarnings("unchecked")
  public static JsonObject loadConfig(String pathStr) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(pathStr), "empty config path!");
    Path path = Paths.get(pathStr);
    Preconditions.checkArgument(
        Files.exists(path) && Files.isRegularFile(path) && Files.isReadable(path),
        "Illegal config path: %s",
        pathStr);
    try (InputStream inputStream = Files.newInputStream(path)) {
      Yaml yaml = YAML_FAST_THREAD_LOCAL.get();
      Object load = yaml.load(inputStream);
      Preconditions.checkArgument(load instanceof Map, "%s can't be casted to Map!", load);
      return new JsonObject((Map<String, Object>) load);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
