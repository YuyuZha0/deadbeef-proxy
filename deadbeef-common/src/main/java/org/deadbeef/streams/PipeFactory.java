package org.deadbeef.streams;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;

import java.util.function.Function;

// TODO we may add some PipeImpl with metric later
public interface PipeFactory extends Function<ReadStream<Buffer>, Pipe<Buffer>> {

  Pipe<Buffer> newPipe(ReadStream<Buffer> src);

  @Override
  default Pipe<Buffer> apply(ReadStream<Buffer> src) {
    return newPipe(src);
  }
}
