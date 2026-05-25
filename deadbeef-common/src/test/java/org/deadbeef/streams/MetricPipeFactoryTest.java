package org.deadbeef.streams;

import com.codahale.metrics.MetricRegistry;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MetricPipeFactoryTest {

  @SuppressWarnings("unchecked")
  @Test
  public void counterAndMeterAreRegisteredWithCamelCasedName() {
    MetricRegistry registry = new MetricRegistry();
    new MetricPipeFactory(registry, StreamType.HTTP_UP);

    assertTrue(registry.getCounters().containsKey("HttpUp[NewPipeCnt]"));
    assertTrue(registry.getMeters().containsKey("HttpUp[BytesRead]"));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void newPipeIncrementsCounter() {
    MetricRegistry registry = new MetricRegistry();
    MetricPipeFactory factory = new MetricPipeFactory(registry, StreamType.HTTPS_DOWN);

    ReadStream<Buffer> src = Mockito.mock(ReadStream.class);
    Mockito.when(src.endHandler(Mockito.any())).thenReturn(src);
    Mockito.when(src.exceptionHandler(Mockito.any())).thenReturn(src);

    Pipe<Buffer> a = factory.newPipe(src);
    Pipe<Buffer> b = factory.newPipe(src);

    assertNotNull(a);
    assertNotNull(b);
    assertEquals(2L, registry.counter("HttpsDown[NewPipeCnt]").getCount());
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullRegistry() {
    new MetricPipeFactory(null, StreamType.HTTP_UP);
  }

  @Test(expected = NullPointerException.class)
  public void rejectsNullStreamType() {
    new MetricPipeFactory(new MetricRegistry(), null);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void allStreamTypesRegisterDistinctNames() {
    MetricRegistry registry = new MetricRegistry();
    for (StreamType type : StreamType.values()) {
      new MetricPipeFactory(registry, type);
    }
    // 4 stream types * 2 metrics each = 8 unique names total
    assertEquals(4, registry.getCounters().size());
    assertEquals(4, registry.getMeters().size());
  }
}
