package org.deadbeef.bootstrap;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Preconditions;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.util.Constants;
import org.deadbeef.util.YAMLMapperFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Slf4j
public final class Bootstrap {

  private static final String LOGO =
      "\n"
          + " ________  _______   ________  ________  ________  _______   _______   ________ \n"
          + "|\\   ___ \\|\\  ___ \\ |\\   __  \\|\\   ___ \\|\\   __  \\|\\  ___ \\ |\\  ___ \\ |\\  _____\\\n"
          + "\\ \\  \\_|\\ \\ \\   __/|\\ \\  \\|\\  \\ \\  \\_|\\ \\ \\  \\|\\ /\\ \\   __/|\\ \\   __/|\\ \\  \\__/ \n"
          + " \\ \\  \\ \\\\ \\ \\  \\_|/_\\ \\   __  \\ \\  \\ \\\\ \\ \\   __  \\ \\  \\_|/_\\ \\  \\_|/_\\ \\   __\\\n"
          + "  \\ \\  \\_\\\\ \\ \\  \\_|\\ \\ \\  \\ \\  \\ \\  \\_\\\\ \\ \\  \\|\\  \\ \\  \\_|\\ \\ \\  \\_|\\ \\ \\  \\_|\n"
          + "   \\ \\_______\\ \\_______\\ \\__\\ \\__\\ \\_______\\ \\_______\\ \\_______\\ \\_______\\ \\__\\ \n"
          + "    \\|_______|\\|_______|\\|__|\\|__|\\|_______|\\|_______|\\|_______|\\|_______|\\|__| \n"
          + "                                                                                \n";

  private static final String BYE =
      "\n"
          + "  ____             _ \n"
          + " |  _ \\           | |\n"
          + " | |_) |_   _  ___| |\n"
          + " |  _ <| | | |/ _ \\ |\n"
          + " | |_) | |_| |  __/_|\n"
          + " |____/ \\__, |\\___(_)\n"
          + "         __/ |       \n"
          + "        |___/        \n";

  static {
    System.setProperty(
        "vertx.logger-delegate-factory-class-name", SLF4JLogDelegateFactory.class.getName());
    InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
  }

  private Bootstrap() {
    throw new IllegalStateException();
  }

  private static String replaceLineSeparator(String input) {
    String s = System.lineSeparator();
    if ("\n".equals(s)) {
      return input;
    } else {
      return StringUtils.replace(input, "\n", s);
    }
  }

  public static void printLogo() {
    System.out.println(replaceLineSeparator(LOGO));
  }

  public static <C extends ProxyConfig> C loadYamlFileConfig(
      String pathStr, @NonNull Class<C> type) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(pathStr), "Empty config path!");
    Path path = Paths.get(pathStr);
    Preconditions.checkArgument(
        Files.exists(path) && Files.isRegularFile(path) && Files.isReadable(path),
        "Illegal config path: %s",
        pathStr);
    try (InputStream inputStream = Files.newInputStream(path)) {
      YAMLMapper yamlMapper = new YAMLMapperFactory().get();
      return yamlMapper.readValue(inputStream, type);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <C extends ProxyConfig> C loadCommandLineConfig(String[] args, Class<C> type) {
    CLI cli =
        CLI.create("deadbeef")
            .addOption(
                new Option()
                    .setShortName("c")
                    .setLongName("config")
                    .setDescription("yaml config path")
                    .setSingleValued(true)
                    .setArgName("config-path"));

    CommandLine commandLine = cli.parse(Arrays.asList(args), true);
    C config = loadYamlFileConfig(commandLine.getOptionValue("c"), type);
    config.verify();
    if (log.isDebugEnabled()) {
      log.debug("Load config successfully:{}{}", Constants.lineSeparator(), config);
    }
    return config;
  }

  public static <A extends ProxyVerticle<C>, C extends ProxyConfig> void deploy(
      @NonNull Vertx vertx, @NonNull Function<C, A> factory, @NonNull C config) {
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

  public static Vertx vertx(@NonNull ProxyConfig config) {
    VertxOptions vertxOptions = new VertxOptions();
    if (config.getPreferNativeTransport() != null) {
      vertxOptions.setPreferNativeTransport(config.getPreferNativeTransport());
    }
    List<String> addressResolverArray = config.getAddressResolver();
    if (addressResolverArray != null && !addressResolverArray.isEmpty()) {
      AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
      for (String s : addressResolverArray) {
        addressResolverOptions.addServer(s);
      }
      vertxOptions.setAddressResolverOptions(addressResolverOptions);
    }

    return addShutdownHook(Vertx.vertx(vertxOptions));
  }

  private static Vertx addShutdownHook(Vertx vertx) {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    vertx.close().toCompletionStage().toCompletableFuture().join();
                  } catch (Exception ignore) {
                  }
                  // We can't use log, log frameworks may have its own shutdownHooks.
                  System.out.println(replaceLineSeparator(BYE));
                }));
    return vertx;
  }

  public static <A extends ProxyVerticle<C>, C extends ProxyConfig> void bootstrap(
      Function<C, A> factory, C config) {
    Vertx vertx = vertx(config);
    deploy(vertx, factory, config);
  }

  public static <A extends ProxyVerticle<C>, C extends ProxyConfig> void bootstrap(
      Function<C, A> factory, String[] args, Class<C> configType) {
    bootstrap(factory, loadCommandLineConfig(args, configType));
  }
}
