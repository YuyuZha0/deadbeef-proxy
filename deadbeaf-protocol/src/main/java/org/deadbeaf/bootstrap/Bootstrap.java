package org.deadbeaf.bootstrap;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
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

  private Bootstrap() {
    throw new IllegalStateException();
  }

  private static JsonObject loadConfig(String[] args) {
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
      @NonNull Function<JsonObject, A> factory, String[] args) {
    JsonObject config = loadConfig(args);
    Vertx vertx =
        Vertx.vertx(
            new VertxOptions()
                .setPreferNativeTransport(
                    config.getBoolean("preferNativeTransport", Boolean.TRUE)));
    vertx.deployVerticle(
        () -> factory.apply(config),
        new DeploymentOptions(),
        result -> {
          String deployID = result.result();
          log.info("Native transport enable status: {}", vertx.isNativeTransportEnabled());
          if (result.succeeded()) {
            log.info("Deploy verticle successfully: {}", deployID);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> vertx.undeploy(deployID)));
          } else {
            log.error("Deploy verticle with unexpected exception: ", result.cause());
          }
        });
  }
}
