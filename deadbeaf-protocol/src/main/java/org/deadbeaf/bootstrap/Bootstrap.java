package org.deadbeaf.bootstrap;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.deadbeaf.util.Constants;
import org.deadbeaf.util.Utils;

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

  public static JsonObject loadConfig(String[] args) {
    Options options = new Options().addOption("c", "config", true, "yaml config path");
    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine;
    try {
      commandLine = parser.parse(options, args);
    } catch (ParseException e) {
      throw new IllegalArgumentException(e);
    }
    JsonObject config = Utils.loadConfig(commandLine.getOptionValue('c'));
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

  public static <A extends ProxyVerticle> void bootstrap(
      Function<JsonObject, A> factory, JsonObject config) {
    Vertx vertx =
        Vertx.vertx(
            new VertxOptions()
                .setPreferNativeTransport(
                    config.getBoolean("preferNativeTransport", Boolean.TRUE)));
    bootstrap(vertx, factory, config);
  }

  public static <A extends ProxyVerticle> void bootstrap(
      Function<JsonObject, A> factory, String[] args) {
    bootstrap(factory, loadConfig(args));
  }
}
