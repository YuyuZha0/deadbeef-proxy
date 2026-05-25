package org.deadbeef.client;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClientConfigTest {

  private static ClientConfig newConfig() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper yaml =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    String src =
        "remoteHost: remote.example.com\n"
            + "remotePort: 14483\n"
            + "localPort: 14482\n"
            + "secretId: an-id\n"
            + "secretKey: a-key\n";
    return yaml.readValue(src, ClientConfig.class);
  }

  @Test
  public void verifyPassesOnValidConfig() throws Exception {
    newConfig().verify();
  }

  @Test
  public void verifyRejectsBlankRemoteHost() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper yaml =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    ClientConfig cfg =
        yaml.readValue(
            "remoteHost: ' '\nremotePort: 14483\nlocalPort: 14482\nsecretId: x\nsecretKey: y\n",
            ClientConfig.class);
    try {
      cfg.verify();
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("remoteHost"));
    }
  }

  @Test
  public void verifyRejectsZeroLocalPort() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper yaml =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    ClientConfig cfg =
        yaml.readValue(
            "remoteHost: r\nremotePort: 14483\nlocalPort: 0\nsecretId: x\nsecretKey: y\n",
            ClientConfig.class);
    try {
      cfg.verify();
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("localPort"));
    }
  }

  @Test
  public void verifyRejectsBlankSecretId() throws Exception {
    com.fasterxml.jackson.dataformat.yaml.YAMLMapper yaml =
        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
    ClientConfig cfg =
        yaml.readValue(
            "remoteHost: r\nremotePort: 14483\nlocalPort: 14482\nsecretId: ''\nsecretKey: y\n",
            ClientConfig.class);
    try {
      cfg.verify();
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("secretId"));
    }
  }
}
