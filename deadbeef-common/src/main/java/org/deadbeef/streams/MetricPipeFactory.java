package org.deadbeef.streams;

import com.codahale.metrics.MetricRegistry;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import lombok.NonNull;

public final class MetricPipeFactory implements PipeFactory {

  private final MetricRegistry metricRegistry;
  private final boolean endOnSuccess;
  private final boolean endOnFailure;

  public MetricPipeFactory(
      @NonNull MetricRegistry metricRegistry, boolean endOnSuccess, boolean endOnFailure) {
    this.metricRegistry = metricRegistry;
    this.endOnSuccess = endOnSuccess;
    this.endOnFailure = endOnFailure;
  }

  public MetricPipeFactory(MetricRegistry metricRegistry) {
    this(metricRegistry, false, false);
  }

  @Override
  public Pipe<Buffer> newPipe(@NonNull ReadStream<Buffer> src) {
    String name = generalNameOf(src);
    metricRegistry.counter(name.concat("-NewPipeCnt")).inc();
    return new MetricPipeImpl(src, metricRegistry.meter(name.concat("-BytesRead")))
        .endOnSuccess(endOnSuccess)
        .endOnFailure(endOnFailure);
  }

  private String generalNameOf(ReadStream<Buffer> src) {
    if (src instanceof NetSocket) {
      // should be a socket facade of HttpConnection
      if (src.getClass() != NetSocketImpl.class) {
        return "NetSocket0";
      }
      return "NetSocket";
    }
    if (src instanceof HttpServerRequest) {
      return "HttpServerRequest";
    }
    if (src instanceof HttpClientResponse) {
      return "HttpClientResponse";
    }
    return "Unknown";
  }
}
