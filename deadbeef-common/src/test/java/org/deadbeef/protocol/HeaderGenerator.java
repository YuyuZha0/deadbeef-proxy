package org.deadbeef.protocol;

import com.google.common.base.CaseFormat;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class HeaderGenerator {

  @Test
  public void test() {
    Class<HttpHeaderNames> headerNamesClass = HttpHeaderNames.class;
    int index = 1;
    for (Field field : headerNamesClass.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          && Modifier.isFinal(field.getModifiers())
          && AsciiString.class.isAssignableFrom(field.getType())) {
        System.out.printf(
            "optional string %s = %d;%n",
            CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_UNDERSCORE, field.getName()), ++index);
      }
    }
  }

  @Test
  public void test1() {}
}
