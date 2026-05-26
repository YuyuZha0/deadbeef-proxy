package org.deadbeef.streams;

import com.codahale.metrics.Meter;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import lombok.NonNull;

/**
 * Pipe factory that meters every buffer flowing through. The caller hands in the {@link Meter}
 * directly — naming and registry ownership belong to {@code ProxyMetrics}, not to this class.
 */
public final class MetricPipeFactory implements PipeFactory {

  private final Meter meter;
  private final boolean endOnSuccess;
  private final boolean endOnFailure;

  public MetricPipeFactory(@NonNull Meter meter, boolean endOnSuccess, boolean endOnFailure) {
    this.meter = meter;
    this.endOnSuccess = endOnSuccess;
    this.endOnFailure = endOnFailure;
  }

  public MetricPipeFactory(@NonNull Meter meter) {
    this(meter, false, false);
  }

  @Override
  public Pipe<Buffer> newPipe(@NonNull ReadStream<Buffer> src) {
    return new MetricPipeImpl(src, meter).endOnSuccess(endOnSuccess).endOnFailure(endOnFailure);
  }
}
