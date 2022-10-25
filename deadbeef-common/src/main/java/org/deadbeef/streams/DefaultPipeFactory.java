package org.deadbeef.streams;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.impl.PipeImpl;
import lombok.NonNull;

public final class DefaultPipeFactory implements PipeFactory {

  private final boolean endOnSuccess;
  private final boolean endOnFailure;

  public DefaultPipeFactory(boolean endOnSuccess, boolean endOnFailure) {
    this.endOnSuccess = endOnSuccess;
    this.endOnFailure = endOnFailure;
  }

  public DefaultPipeFactory() {
    this(false, false);
  }

  @Override
  public Pipe<Buffer> newPipe(@NonNull ReadStream<Buffer> src) {
    return new PipeImpl<>(src).endOnSuccess(endOnSuccess).endOnFailure(endOnFailure);
  }
}
