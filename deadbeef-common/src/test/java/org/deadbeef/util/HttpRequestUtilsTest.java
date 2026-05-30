package org.deadbeef.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.NoStackTraceThrowable;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class HttpRequestUtilsTest {

  @Test
  public void contentLengthHandlesNullAndEmpty() {
    assertEquals(0L, HttpRequestUtils.contentLength(null));
    assertEquals(0L, HttpRequestUtils.contentLength(MultiMap.caseInsensitiveMultiMap()));
  }

  @Test
  public void contentLengthParsesNumeric() {
    MultiMap m = MultiMap.caseInsensitiveMultiMap();
    m.add(HttpHeaderNames.CONTENT_LENGTH, "123");
    assertEquals(123L, HttpRequestUtils.contentLength(m));
  }

  @Test
  public void contentLengthTransferEncodingReturnsMinusOne() {
    MultiMap m = MultiMap.caseInsensitiveMultiMap();
    m.add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
    assertEquals(-1L, HttpRequestUtils.contentLength(m));
  }

  @Test
  public void contentLengthMalformedNumericReturnsZero() {
    MultiMap m = MultiMap.caseInsensitiveMultiMap();
    m.add(HttpHeaderNames.CONTENT_LENGTH, "not-a-number");
    assertEquals(0L, HttpRequestUtils.contentLength(m));
  }

  @Test
  public void errorMappingTimeoutReturnsGatewayTimeout() {
    assertEquals(
        HttpResponseStatus.GATEWAY_TIMEOUT,
        HttpRequestUtils.errorMapping(new TimeoutException("slow")));
    assertEquals(
        HttpResponseStatus.GATEWAY_TIMEOUT,
        HttpRequestUtils.errorMapping(io.netty.handler.timeout.ReadTimeoutException.INSTANCE));
  }

  @Test
  public void errorMappingOtherReturnsBadGateway() {
    assertEquals(
        HttpResponseStatus.BAD_GATEWAY, HttpRequestUtils.errorMapping(new IOException("boom")));
    assertEquals(
        HttpResponseStatus.BAD_GATEWAY, HttpRequestUtils.errorMapping(new RuntimeException()));
  }

  @Test
  public void createErrorHandlerWritesBodyWhenResponseOpen() {
    HttpServerResponse response = Mockito.mock(HttpServerResponse.class);
    Mockito.when(response.closed()).thenReturn(false);
    Mockito.when(response.ended()).thenReturn(false);
    Mockito.when(response.setStatusCode(Mockito.anyInt())).thenReturn(response);
    Mockito.when(response.putHeader(any(CharSequence.class), any(CharSequence.class)))
        .thenReturn(response);

    Handler<Throwable> handler = HttpRequestUtils.createErrorHandler(response);
    handler.handle(new NoStackTraceThrowable("boom"));

    ArgumentCaptor<Integer> status = ArgumentCaptor.forClass(Integer.class);
    Mockito.verify(response).setStatusCode(status.capture());
    assertEquals(HttpResponseStatus.BAD_GATEWAY.code(), (int) status.getValue());

    ArgumentCaptor<Buffer> body = ArgumentCaptor.forClass(Buffer.class);
    Mockito.verify(response).end(body.capture());
    assertEquals("boom", body.getValue().toString());
  }

  @Test
  public void createErrorHandlerSkipsClosedResponse() {
    HttpServerResponse response = Mockito.mock(HttpServerResponse.class);
    Mockito.when(response.closed()).thenReturn(true);

    Handler<Throwable> handler = HttpRequestUtils.createErrorHandler(response);
    handler.handle(new RuntimeException("ignore"));

    Mockito.verify(response, Mockito.never()).setStatusCode(Mockito.anyInt());
    Mockito.verify(response, Mockito.never()).end(any(Buffer.class));
  }

  @Test
  public void createErrorHandlerFiresAtMostOnce() {
    HttpServerResponse response = Mockito.mock(HttpServerResponse.class);
    Mockito.when(response.closed()).thenReturn(false);
    Mockito.when(response.ended()).thenReturn(false);
    Mockito.when(response.setStatusCode(Mockito.anyInt())).thenReturn(response);
    Mockito.when(response.putHeader(any(CharSequence.class), any(CharSequence.class)))
        .thenReturn(response);

    Handler<Throwable> handler = HttpRequestUtils.createErrorHandler(response);
    handler.handle(new NoStackTraceThrowable("first"));
    handler.handle(new NoStackTraceThrowable("second"));

    Mockito.verify(response, Mockito.times(1)).end(any(Buffer.class));
  }

  @SuppressWarnings("unused")
  private void unusedAsyncResultRef(AsyncResult<Void> r) {}
}
