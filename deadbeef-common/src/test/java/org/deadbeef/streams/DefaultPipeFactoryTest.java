package org.deadbeef.streams;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

public class DefaultPipeFactoryTest {

  @SuppressWarnings("unchecked")
  @Test
  public void newPipeReturnsPipeInstance() {
    ReadStream<Buffer> src = Mockito.mock(ReadStream.class);
    Mockito.when(src.pause()).thenReturn(src);
    Mockito.when(src.handler(Mockito.any())).thenReturn(src);

    DefaultPipeFactory factory = new DefaultPipeFactory();
    Pipe<Buffer> pipe = factory.newPipe(src);

    assertNotNull(pipe);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void factoryReturnsDistinctPipesPerCall() {
    ReadStream<Buffer> src1 = Mockito.mock(ReadStream.class);
    ReadStream<Buffer> src2 = Mockito.mock(ReadStream.class);
    Mockito.when(src1.pause()).thenReturn(src1);
    Mockito.when(src1.handler(Mockito.any())).thenReturn(src1);
    Mockito.when(src2.pause()).thenReturn(src2);
    Mockito.when(src2.handler(Mockito.any())).thenReturn(src2);

    DefaultPipeFactory factory = new DefaultPipeFactory(true, false);
    Pipe<Buffer> a = factory.newPipe(src1);
    Pipe<Buffer> b = factory.newPipe(src2);

    assertNotSame(a, b);
  }

  @SuppressWarnings("unchecked")
  @Test(expected = NullPointerException.class)
  public void rejectsNullSource() {
    new DefaultPipeFactory().newPipe(null);
  }

  @Test
  public void applyDelegatesToNewPipe() {
    @SuppressWarnings("unchecked")
    ReadStream<Buffer> src = Mockito.mock(ReadStream.class);
    Mockito.when(src.pause()).thenReturn(src);
    Mockito.when(src.handler(Mockito.any())).thenReturn(src);

    PipeFactory factory = new DefaultPipeFactory();
    assertNotNull(factory.apply(src));
  }
}
