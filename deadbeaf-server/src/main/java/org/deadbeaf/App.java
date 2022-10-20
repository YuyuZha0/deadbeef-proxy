package org.deadbeaf;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.bootstrap.Bootstrap;
import org.deadbeaf.server.HttpVerticle;
import org.deadbeaf.server.HttpsVerticle;

@Slf4j
public final class App {

  public static void main(String[] args) {
    // args = new String[] {"-c", "/Users/zhaoyuyu/IdeaProjects/deadbeaf-proxy/server_config.yaml"};
    JsonObject config = Bootstrap.loadConfig(args);
    Vertx vertx =
        Vertx.vertx(
            new VertxOptions()
                .setPreferNativeTransport(
                    config.getBoolean("preferNativeTransport", Boolean.TRUE)));
    Bootstrap.bootstrap(vertx, HttpsVerticle::new, config);
    Bootstrap.bootstrap(vertx, HttpVerticle::new, config);
  }
}
