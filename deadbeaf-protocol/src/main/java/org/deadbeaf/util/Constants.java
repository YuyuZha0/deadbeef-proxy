package org.deadbeaf.util;

import io.netty.util.AsciiString;

import java.util.concurrent.TimeUnit;

public final class Constants {

  private static final AsciiString AUTH_HEADER_NAME = AsciiString.cached("X-Deadbeaf-Auth");
  private static final int REQUEST_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(20);

  private Constants() {
    throw new IllegalStateException();
  }

  public static String lineSeparator() {
    return System.lineSeparator();
  }

  public static String rightArrow() {
    return "[-->]";
  }

  public static String leftArrow() {
    return "[<--]";
  }

  public static CharSequence authHeaderName() {
    return AUTH_HEADER_NAME;
  }

  public static int requestTimeout() {
    return REQUEST_TIMEOUT;
  }
}
