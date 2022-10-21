package org.deadbeef.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PrefixTest {

  @Test
  public void verifyMagic() {
    assertEquals("DEADBEEF", Integer.toHexString(Prefix.MAGIC).toUpperCase());
  }
}
