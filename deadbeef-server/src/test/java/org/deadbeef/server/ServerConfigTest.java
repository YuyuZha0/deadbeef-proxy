package org.deadbeef.server;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ServerConfigTest {

  private static final YAMLMapper YAML = new YAMLMapper();

  @Test
  public void verifyPassesOnValidConfig() throws Exception {
    ServerConfig cfg =
        YAML.readValue(
            "port: 14483\nauth:\n  - { secretId: a, secretKey: b }\n",
            ServerConfig.class);
    cfg.verify();
  }

  @Test
  public void verifyRejectsMissingAuth() throws Exception {
    ServerConfig cfg = YAML.readValue("port: 14483\n", ServerConfig.class);
    try {
      cfg.verify();
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().toLowerCase().contains("auth"));
    }
  }

  @Test
  public void verifyRejectsBadPort() throws Exception {
    ServerConfig cfg =
        YAML.readValue(
            "port: 0\nauth:\n  - { secretId: a, secretKey: b }\n", ServerConfig.class);
    try {
      cfg.verify();
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("port"));
    }
  }

  @Test
  public void authTupleExposesKeyValue() {
    ServerConfig.AuthTuple tuple = new ServerConfig.AuthTuple("s-id", "s-key");
    assertEquals("s-id", tuple.getSecretId());
    assertEquals("s-key", tuple.getSecretKey());
    assertEquals("s-id", tuple.getKey());
    assertEquals("s-key", tuple.getValue());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void authTupleSetValueUnsupported() {
    new ServerConfig.AuthTuple("a", "b").setValue("c");
  }
}
