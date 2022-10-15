package org.deadbeaf.protocol;

import com.google.protobuf.GeneratedMessageV3;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;

import java.io.IOException;

public final class Prefix {

  public static final int FIXED = 8;
  static final int MAGIC = 0xDEADBEEF;

  private Prefix() {
    throw new UnsupportedOperationException();
  }

  private static ByteBuf newByteBuf(int size) {
    return VertxByteBufAllocator.UNPOOLED_ALLOCATOR.buffer(size);
  }

  public static Buffer serializeToBuffer(byte[] data) {
    ByteBuf byteBuf = newByteBuf(FIXED + data.length);
    byteBuf.writeInt(MAGIC);
    byteBuf.writeInt(data.length);
    byteBuf.writeBytes(data);
    return Buffer.buffer(byteBuf);
  }

  public static Buffer serializeToBuffer(GeneratedMessageV3 data) {
    int serializedSize = data.getSerializedSize();
    ByteBuf byteBuf = newByteBuf(FIXED + serializedSize);
    byteBuf.writeInt(MAGIC);
    byteBuf.writeInt(serializedSize);
    try (ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
      data.writeTo(outputStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return Buffer.buffer(byteBuf);
  }

  public static int serializeToBufferSize(GeneratedMessageV3 data) {
    return FIXED + data.getSerializedSize();
  }
}
