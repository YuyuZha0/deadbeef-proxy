package org.deadbeef.streams;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

public class MetricPipeFactoryTest {

  @SuppressWarnings("unchecked")
  @Test
  public void newPipeProducesPipeInstance() {
    Meter meter = new MetricRegistry().meter("test.bytes");
    MetricPipeFactory factory = new MetricPipeFactory(meter);

    ReadStream<Buffer> src = Mockito.mock(ReadStream.class);
    Mockito.when(src.endHandler(Mockito.any())).thenReturn(src);
    Mockito.when(src.exceptionHandler(Mockito.any())).thenReturn(src);

    Pipe<Buffer> pipe = factory.newPipe(src);

    assertNotNull(pipe);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void factoryReturnsDistinctPipesPerCall() {
    Meter meter = new MetricRegistry().meter("test.bytes");
    MetricPipeFactory factory = new MetricPipeFactory(meter, true, false);

    ReadStream<Buffer> src1 = Mockito.mock(ReadStream.class);
    ReadStream<Buffer> src2 = Mockito.mock(ReadStream.class);
    Mockito.when(src1.endHandler(Mockito.any())).thenReturn(src1);
    Mockito.when(src1.exceptionHandler(Mockito.any())).thenReturn(src1);
    Mockito.when(src2.endHandler(Mockito.any())).thenReturn(src2);
    Mockito.when(src2.exceptionHandler(Mockito.any())).thenReturn(src2);

    assertNotSame(factory.newPipe(src1), factory.newPipe(src2));
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullMeter() {
    new MetricPipeFactory(null);
  }
}
