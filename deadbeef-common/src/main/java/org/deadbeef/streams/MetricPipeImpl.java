package org.deadbeef.streams;

import com.codahale.metrics.Meter;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import lombok.NonNull;
import org.deadbeef.util.Utils;

final class MetricPipeImpl implements Pipe<Buffer> {

  private final Promise<Void> result;
  private final ReadStream<Buffer> src;
  private final Meter meter;
  private boolean endOnSuccess = true;
  private boolean endOnFailure = true;
  private WriteStream<Buffer> dst;

  MetricPipeImpl(ReadStream<Buffer> src, Meter meter) {
    this.src = src;
    this.meter = meter;
    this.result = Promise.promise();

    // Set handlers now
    src.endHandler(result::tryComplete);
    src.exceptionHandler(result::tryFail);
  }

  private static void handleSuccess(
      Handler<AsyncResult<Void>> completionHandler, WriteStream<Buffer> dst, boolean endOnSuccess) {
    if (endOnSuccess) {
      dst.end(completionHandler);
    } else {
      completionHandler.handle(Future.succeededFuture());
    }
  }

  private static void handleFailure(
      Throwable cause,
      Handler<AsyncResult<Void>> completionHandler,
      WriteStream<Buffer> dst,
      boolean endOnFailure) {
    Future<Void> res = Future.failedFuture(cause);
    if (endOnFailure) {
      dst.end(ignore -> completionHandler.handle(res));
    } else {
      completionHandler.handle(res);
    }
  }

  @Override
  public synchronized Pipe<Buffer> endOnFailure(boolean end) {
    endOnFailure = end;
    return this;
  }

  @Override
  public synchronized Pipe<Buffer> endOnSuccess(boolean end) {
    endOnSuccess = end;
    return this;
  }

  @Override
  public synchronized Pipe<Buffer> endOnComplete(boolean end) {
    endOnSuccess = end;
    endOnFailure = end;
    return this;
  }

  private void handleWriteResult(AsyncResult<Void> ack) {
    if (ack.failed()) {
      result.tryFail(new WriteException(ack.cause()));
    }
  }

  @Override
  public void to(@NonNull WriteStream<Buffer> ws, Handler<AsyncResult<Void>> completionHandler) {
    boolean endOnSuccess, endOnFailure;
    synchronized (this) {
      if (dst != null) {
        throw new IllegalStateException();
      }
      dst = ws;
      endOnSuccess = this.endOnSuccess;
      endOnFailure = this.endOnFailure;
    }
    Handler<Void> drainHandler = v -> src.resume();
    src.handler(
        item -> {
          meter.mark(item.length());
          ws.write(item, this::handleWriteResult);
          if (ws.writeQueueFull()) {
            src.pause();
            ws.drainHandler(drainHandler);
          }
        });
    src.resume();
    result
        .future()
        .onComplete(
            ar -> {
              Utils.clearHandlers(src);
              if (ar.succeeded()) {
                handleSuccess(completionHandler, ws, endOnSuccess);
              } else {
                Throwable err = ar.cause();
                if (err instanceof WriteException) {
                  src.resume();
                  err = err.getCause();
                }
                handleFailure(err, completionHandler, ws, endOnFailure);
              }
            });
  }

  public void close() {
    synchronized (this) {
      src.exceptionHandler(null);
      src.handler(null);
      if (dst != null) {
        dst.drainHandler(null);
        dst.exceptionHandler(null);
      }
    }
    VertxException err = new VertxException("Pipe closed", true);
    if (result.tryFail(err)) {
      src.resume();
    }
  }

  private static class WriteException extends VertxException {
    private WriteException(Throwable cause) {
      super(cause, true);
    }
  }
}
