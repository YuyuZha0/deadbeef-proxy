package org.deadbeef.streams;

import io.vertx.core.buffer.Buffer;
import org.deadbeef.protocol.HttpProto;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PrefixTest {

  @Test
  public void verifyMagic() {
    assertEquals("DEADBEEF", Integer.toHexString(Prefix.MAGIC).toUpperCase());
  }

  @Test
  public void serializeBytesAndParseBack() {
    byte[] payload = "hello-world".getBytes(StandardCharsets.UTF_8);

    Buffer buf = Prefix.serializeToBuffer(payload);

    assertEquals(Prefix.FIXED + payload.length, buf.length());
    assertEquals(Prefix.MAGIC, buf.getInt(0));
    assertEquals(payload.length, buf.getInt(4));
    byte[] body = buf.getBytes(Prefix.FIXED, buf.length());
    assertEquals(payload.length, body.length);
    for (int i = 0; i < payload.length; i++) {
      assertEquals(payload[i], body[i]);
    }
  }

  @Test
  public void serializeProtoAndParseBack() throws Exception {
    HttpProto.Request original =
        HttpProto.Request.newBuilder()
            .setMethod(HttpProto.Method.POST)
            .setScheme("https")
            .setVersion(HttpProto.Version.HTTP_1_1)
            .setHeaders(HttpProto.Headers.newBuilder().setAccept("*/*"))
            .build();

    Buffer buf = Prefix.serializeToBuffer(original);

    assertEquals(Prefix.MAGIC, buf.getInt(0));
    int bodyLen = buf.getInt(4);
    assertEquals(original.getSerializedSize(), bodyLen);
    HttpProto.Request decoded =
        HttpProto.Request.parseFrom(buf.getBytes(Prefix.FIXED, Prefix.FIXED + bodyLen));
    assertEquals(original, decoded);
  }

  @Test
  public void serializeToBufferSizeAddsFixed() {
    HttpProto.Request msg =
        HttpProto.Request.newBuilder().setMethod(HttpProto.Method.GET).build();

    assertEquals(Prefix.FIXED + msg.getSerializedSize(), Prefix.serializeToBufferSize(msg));
  }

  @Test
  public void fixedHeaderIsEightBytes() {
    assertEquals(8, Prefix.FIXED);
    Buffer buf = Prefix.serializeToBuffer(new byte[0]);
    assertTrue(buf.length() >= Prefix.FIXED);
  }
}
