package org.deadbeef;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.bootstrap.Bootstrap;
import org.deadbeef.server.HttpVerticle;
import org.deadbeef.server.HttpsVerticle;

@Slf4j
public final class App {

  public static void main(String[] args) {
    // args = new String[] {"-c", "/Users/zhaoyuyu/IdeaProjects/deadbeaf-proxy/server_config.yaml"};
    JsonObject config = Bootstrap.loadCommandLineConfig(args);
    Vertx vertx = Bootstrap.vertx(config);
    Bootstrap.bootstrap(vertx, HttpsVerticle::new, config);
    Bootstrap.bootstrap(vertx, HttpVerticle::new, config);
  }
}
