package org.deadbeef.streams;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreamTypeTest {

  @Test
  public void fourTypesDefined() {
    assertEquals(4, StreamType.values().length);
  }

  @Test
  public void valueOfRoundTrip() {
    for (StreamType t : StreamType.values()) {
      assertEquals(t, StreamType.valueOf(t.name()));
    }
  }
}
