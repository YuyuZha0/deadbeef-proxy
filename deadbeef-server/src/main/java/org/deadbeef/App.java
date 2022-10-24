package org.deadbeef;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.bootstrap.Bootstrap;
import org.deadbeef.server.HttpVerticle;
import org.deadbeef.server.HttpsVerticle;
import org.deadbeef.server.ServerConfig;

@Slf4j
public final class App {

  public static void main(String[] args) {
    // args = new String[] {"-c", "/Users/zhaoyuyu/IdeaProjects/deadbeef-proxy/server-config.yaml"};
    ServerConfig config = Bootstrap.loadCommandLineConfig(args, ServerConfig.class);
    Vertx vertx = Bootstrap.vertx(config);
    Bootstrap.deploy(vertx, HttpsVerticle::new, config);
    Bootstrap.deploy(vertx, HttpVerticle::new, config);
  }
}
