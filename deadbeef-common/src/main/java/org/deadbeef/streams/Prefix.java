package org.deadbeef.streams;

import com.google.protobuf.GeneratedMessageV3;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;

import java.io.IOException;

public final class Prefix {

  public static final int FIXED = 8;
  static final int MAGIC = 0xDEADBEEF;

  private Prefix() {
    throw new IllegalStateException();
  }

  private static ByteBuf newByteBuf(int size) {
    return VertxByteBufAllocator.DEFAULT.buffer(size);
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
      ReferenceCountUtil.release(byteBuf);
      throw new VertxException(e);
    }
    return Buffer.buffer(byteBuf);
  }

  public static int serializeToBufferSize(GeneratedMessageV3 data) {
    return FIXED + data.getSerializedSize();
  }
}
