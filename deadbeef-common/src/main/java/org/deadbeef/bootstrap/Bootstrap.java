package org.deadbeef.bootstrap;

import com.google.common.base.Preconditions;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.util.Constants;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;

@Slf4j
public final class Bootstrap {

  static {
    System.setProperty(
        "vertx.logger-delegate-factory-class-name", SLF4JLogDelegateFactory.class.getName());
    InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
  }

  private Bootstrap() {
    throw new IllegalStateException();
  }

  @SuppressWarnings("unchecked")
  public static JsonObject loadYamlFileConfig(String pathStr) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(pathStr), "empty config path!");
    Path path = Paths.get(pathStr);
    Preconditions.checkArgument(
        Files.exists(path) && Files.isRegularFile(path) && Files.isReadable(path),
        "Illegal config path: %s",
        pathStr);
    try (InputStream inputStream = Files.newInputStream(path)) {
      Yaml yaml = new Yaml();
      Object load = yaml.load(inputStream);
      Preconditions.checkArgument(load instanceof Map, "%s can't be casted to Map!", load);
      return new JsonObject((Map<String, Object>) load);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static JsonObject loadCommandLineConfig(String[] args) {
    Options options = new Options().addOption("c", "config", true, "yaml config path");
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine;
    try {
      commandLine = parser.parse(options, args);
    } catch (ParseException e) {
      throw new IllegalArgumentException(e);
    }
    JsonObject config = loadYamlFileConfig(commandLine.getOptionValue('c'));
    if (log.isDebugEnabled()) {
      log.debug("Load config successfully:{}{}", Constants.lineSeparator(), config);
    }
    return config;
  }

  public static <A extends ProxyVerticle> void bootstrap(
      @NonNull Vertx vertx, @NonNull Function<JsonObject, A> factory, @NonNull JsonObject config) {
    log.info("Native transport enable status: {}", vertx.isNativeTransportEnabled());
    vertx.deployVerticle(
        () -> factory.apply(config),
        new DeploymentOptions(),
        result -> {
          String deployID = result.result();
          if (result.succeeded()) {
            log.info("Deploy verticle successfully: {}", deployID);
          } else {
            log.error("Deploy verticle with unexpected exception: ", result.cause());
          }
        });
  }

  public static Vertx vertx(JsonObject config) {
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.setPreferNativeTransport(config.getBoolean("preferNativeTransport", Boolean.TRUE));
    JsonArray addressResolverArray = config.getJsonArray("addressResolver");
    if (addressResolverArray != null && !addressResolverArray.isEmpty()) {
      AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
      for (Object element : addressResolverArray) {
        if (element instanceof String) {
          addressResolverOptions.addServer(((String) element));
        }
      }
    }
    return Vertx.vertx(vertxOptions);
  }

  public static <A extends ProxyVerticle> void bootstrap(
      Function<JsonObject, A> factory, JsonObject config) {
    Vertx vertx = vertx(config);
    bootstrap(vertx, factory, config);
  }

  public static <A extends ProxyVerticle> void bootstrap(
      Function<JsonObject, A> factory, String[] args) {
    bootstrap(factory, loadCommandLineConfig(args));
  }
}
