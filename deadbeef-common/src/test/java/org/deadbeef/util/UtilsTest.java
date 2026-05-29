package org.deadbeef.util;

import io.vertx.core.Handler;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.ReadStream;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class UtilsTest {

  @Test
  public void atMostOnceFiresDelegateOnce() {
    AtomicInteger calls = new AtomicInteger();
    Handler<Object> wrapped = Utils.atMostOnce(e -> calls.incrementAndGet());

    wrapped.handle("a");
    wrapped.handle("b");
    wrapped.handle("c");

    assertEquals(1, calls.get());
  }

  @Test
  public void clearHandlersTolerantOfNull() {
    Utils.clearHandlers(null);
  }

  @Test
  public void clearHandlersClearsHandlersWithoutThrowing() {
    ReadStream<?> stream = Mockito.mock(ReadStream.class);
    Mockito.doThrow(new RuntimeException("ignore me")).when(stream).endHandler(Mockito.any());

    Utils.clearHandlers(stream);

    Mockito.verify(stream).handler(Mockito.isNull());
    Mockito.verify(stream).exceptionHandler(Mockito.isNull());
    Mockito.verify(stream).endHandler(Mockito.isNull());
  }

  @Test
  public void exchangeCloseHookPropagatesCloseFromAliceToBob() {
    NetSocket alice = Mockito.mock(NetSocket.class);
    NetSocket bob = Mockito.mock(NetSocket.class);

    Utils.exchangeCloseHook(alice, bob);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Handler<Void>> aliceHook = ArgumentCaptor.forClass(Handler.class);
    Mockito.verify(alice).closeHandler(aliceHook.capture());

    aliceHook.getValue().handle(null);

    Mockito.verify(bob).closeHandler(Mockito.isNull());
    Mockito.verify(bob).close();
  }

  @Test
  public void exchangeCloseHookPropagatesCloseFromBobToAlice() {
    NetSocket alice = Mockito.mock(NetSocket.class);
    NetSocket bob = Mockito.mock(NetSocket.class);

    Utils.exchangeCloseHook(alice, bob);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Handler<Void>> bobHook = ArgumentCaptor.forClass(Handler.class);
    Mockito.verify(bob).closeHandler(bobHook.capture());

    bobHook.getValue().handle(null);

    Mockito.verify(alice).closeHandler(Mockito.isNull());
    Mockito.verify(alice).close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void exchangeCloseHookRejectsSameInstance() {
    NetSocket sock = Mockito.mock(NetSocket.class);
    Utils.exchangeCloseHook(sock, sock);
  }
}
