package org.deadbeaf.protocol;

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.core.streams.impl.PipeImpl;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.util.Constants;

import java.util.function.BiConsumer;

@Slf4j
public final class ProxyStreamPrefixVisitor<W extends WriteStream<Buffer>> {

  private final VertxInternal vertx;

  public ProxyStreamPrefixVisitor(@NonNull Vertx vertx) {
    this.vertx = (VertxInternal) vertx;
  }

  private static void clearHandlers(ReadStream<?> readStream) {
    if (log.isDebugEnabled()) {
      log.debug("Clearing handlers on: {}", readStream);
    }
    try {
      readStream.handler(null);
    } catch (Exception ignore) {
    }
    try {
      readStream.exceptionHandler(null);
    } catch (Exception ignore) {
    }
    try {
      readStream.endHandler(null);
    } catch (Exception ignore) {
    }
  }

  public Future<PrefixAndAction<? super W>> visit(ReadStream<Buffer> src) {
    Promise<PrefixAndAction<? super W>> promise = Promise.promise();
    visit(src, promise);
    return promise.future();
  }

  public void visit(
      @NonNull ReadStream<Buffer> src,
      @NonNull Handler<AsyncResult<PrefixAndAction<? super W>>> handler) {
    if (log.isDebugEnabled()) {
      log.debug("Start resolving prefix on: {}", src);
    }
    PrefixBuffer prefixBuffer = new PrefixBuffer(src);
    prefixBuffer.parsePrefix(
        ar -> {
          if (ar.succeeded()) {
            Buffer prefix = ar.result();
            ContextInternal context = vertx.getOrCreateContext();
            BiConsumer<W, Handler<AsyncResult<Void>>> action =
                (writeStream, whenWritingFinished) ->
                    context.runOnContext(
                        v -> {
                          try {
                            copy(src, writeStream, prefixBuffer, whenWritingFinished);
                          } catch (Throwable cause) {
                            whenWritingFinished.handle(Future.failedFuture(cause));
                          }
                        });
            handler.handle(Future.succeededFuture(new PrefixAndAction<>(prefix, action)));
          } else {
            handler.handle(Future.failedFuture(ar.cause()));
          }
        });
  }

  private void copy(
      ReadStream<Buffer> src,
      W dst,
      PrefixBuffer prefixBuffer,
      Handler<AsyncResult<Void>> handler) {

    Buffer remaining = prefixBuffer.readRemaining();
    prefixBuffer.clear();
    if (remaining == null && prefixBuffer.isStreamEnded()) {
      handler.handle(Future.succeededFuture());
      return;
    }
    if (remaining == null) {
      Pipe<Buffer> pipe = newPipe(src);
      pipe.to(dst, handler);
      src.resume();
      return;
    }
    if (prefixBuffer.isStreamEnded()) {
      dst.write(remaining, handler);
      return;
    }
    dst.write(
        remaining,
        ar -> {
          if (ar.failed()) {
            handler.handle(ar);
          }
        });
    Pipe<Buffer> pipe = newPipe(src);
    pipe.to(dst, handler);
    src.resume();
  }

  private Pipe<Buffer> newPipe(ReadStream<Buffer> src) {
    return new PipeImpl<>(src).endOnSuccess(false).endOnFailure(false);
  }

  private static final class PrefixBuffer implements Handler<Buffer> {

    private static final int BODY_LENGTH_LIMIT = 1 << 23; // 8M
    private final ByteBuf tempBuf = VertxByteBufAllocator.UNPOOLED_ALLOCATOR.heapBuffer();

    private final ReadStream<Buffer> src;

    private final Promise<Buffer> promise;
    private int bodyLen = -1;

    @Getter private volatile boolean streamEnded;

    PrefixBuffer(ReadStream<Buffer> src) {
      this.promise = Promise.promise();
      this.src = src;
      src.endHandler(
          v -> {
            if (log.isDebugEnabled()) {
              log.debug("[ReadStream: `{}`] ended!", src);
            }
            PrefixBuffer.this.streamEnded = true;
          });
      src.exceptionHandler(promise::tryFail);
    }

    private static boolean openCloseContains(int left, int right, int x) {
      return x > left && x <= right;
    }

    private void expectMagicAndLength() {
      int magic = tempBuf.readInt();
      if (magic != Prefix.MAGIC) {
        fetal(
            "Magic not match, expect: 0x%s, but found: 0x%s!",
            Integer.toHexString(Prefix.MAGIC), Integer.toHexString(magic));
        return;
      }
      int bodyLen = tempBuf.readInt();
      if (bodyLen <= 0 || bodyLen > BODY_LENGTH_LIMIT) {
        fetal("Illegal prefix len: %s", bodyLen);
        return;
      }
      this.bodyLen = bodyLen;
      src.fetch(bodyLen);
      if (log.isDebugEnabled()) {
        log.debug("Prefix1 resolved, magic=0x{}, bodyLen={}", Integer.toHexString(magic), bodyLen);
      }
    }

    void parsePrefix(@NonNull Handler<AsyncResult<Buffer>> bufferHandler) {
      src.pause();
      src.fetch(Prefix.FIXED);
      src.handler(this);
      promise
          .future()
          .onComplete(
              result -> {
                clearHandlers(src);
                bufferHandler.handle(result);
              });
    }

    private void expectBody() {
      src.pause(); // pause should happens-before complete promise!!!
      ByteBuf byteBuf = tempBuf.readBytes(bodyLen);
      promise.tryComplete(Buffer.buffer(byteBuf));
      if (log.isDebugEnabled()) {
        log.debug("Prefix2 resolved, readableBytes={}", byteBuf.readableBytes());
      }
    }

    @Override
    public void handle(Buffer event) {
      if (log.isDebugEnabled()) {
        log.info(
            "Incoming bytes:[length={}]{}{}",
            event.length(),
            Constants.lineSeparator(),
            ByteBufUtil.prettyHexDump(event.getByteBuf()));
      }
      int len1 = tempBuf.writerIndex();
      tempBuf.writeBytes(event.getByteBuf());
      int len2 = len1 + event.length();
      if (openCloseContains(len1, len2, Prefix.FIXED)) {
        expectMagicAndLength();
      }
      if (bodyLen > 0 && openCloseContains(len1, len2, bodyLen + Prefix.FIXED)) {
        expectBody();
      }
    }

    void fetal(Throwable cause) {
      promise.tryFail(cause);
    }

    void fetal(String msg, Object... args) {
      fetal(new VertxException(Strings.lenientFormat(msg, args), true));
    }

    Buffer readRemaining() {
      int remainingBytes = tempBuf.readableBytes();
      if (remainingBytes > 0) {
        if (log.isDebugEnabled()) {
          log.debug("Read remaining bytes, length={}", remainingBytes);
        }
        return Buffer.buffer(tempBuf.readBytes(remainingBytes));
      }
      return null;
    }

    void clear() {
      if (log.isDebugEnabled()) {
        log.debug("Release temporary ByteBuf: {}", tempBuf);
      }
      tempBuf.clear();
      ReferenceCountUtil.release(tempBuf);
    }
  }
}
