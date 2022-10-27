package org.deadbeef.streams;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.CaseFormat;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import lombok.NonNull;

public final class MetricPipeFactory implements PipeFactory {

  private final Counter counter;

  private final Meter meter;

  private final boolean endOnSuccess;
  private final boolean endOnFailure;

  public MetricPipeFactory(
      @NonNull MetricRegistry metricRegistry,
      @NonNull StreamType streamType,
      boolean endOnSuccess,
      boolean endOnFailure) {
    String name = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, streamType.name());
    this.counter = metricRegistry.counter(name.concat("[NewPipeCnt]"));
    this.meter = metricRegistry.meter(name.concat("[BytesRead]"));
    this.endOnSuccess = endOnSuccess;
    this.endOnFailure = endOnFailure;
  }

  public MetricPipeFactory(MetricRegistry metricRegistry, StreamType streamType) {
    this(metricRegistry, streamType, false, false);
  }

  @Override
  public Pipe<Buffer> newPipe(@NonNull ReadStream<Buffer> src) {
    counter.inc();
    return new MetricPipeImpl(src, meter).endOnSuccess(endOnSuccess).endOnFailure(endOnFailure);
  }
}
