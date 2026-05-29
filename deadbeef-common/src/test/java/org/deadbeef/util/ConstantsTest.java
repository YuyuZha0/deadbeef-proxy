package org.deadbeef.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConstantsTest {

  @Test
  public void lineSeparatorReturnsSystemValue() {
    assertEquals(System.lineSeparator(), Constants.lineSeparator());
  }

  @Test
  public void arrows() {
    assertEquals("[-->]", Constants.rightArrow());
    assertEquals("[<--]", Constants.leftArrow());
  }

  @Test
  public void authHeaderName() {
    CharSequence name = Constants.authHeaderName();
    assertNotNull(name);
    assertEquals("X-Deadbeef-Auth", name.toString());
  }
}
