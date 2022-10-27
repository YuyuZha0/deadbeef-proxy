package org.deadbeef.client;

import com.codahale.metrics.MetricRegistry;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.protocol.HttpProto;
import org.deadbeef.route.AddressPicker;
import org.deadbeef.streams.MetricPipeFactory;
import org.deadbeef.streams.PipeFactory;
import org.deadbeef.streams.Prefix;
import org.deadbeef.streams.PrefixAndAction;
import org.deadbeef.streams.ProxyStreamPrefixVisitor;
import org.deadbeef.streams.StreamType;
import org.deadbeef.util.Constants;
import org.deadbeef.util.HttpRequestUtils;
import org.deadbeef.util.Utils;

import java.io.IOException;
import java.util.NoSuchElementException;

@Slf4j
public final class Http2SocketHandler implements Handler<HttpServerRequest> {

  private final PipeFactory pipeFactory;
  private final NetClient netClient;
  private final AddressPicker addressPicker;
  private final ProxyStreamPrefixVisitor<NetSocket> proxyStreamPrefixVisitor;
  private final HttpsConnectEncoder httpsConnectEncoder;

  public Http2SocketHandler(
      @NonNull Vertx vertx,
      @NonNull NetClient netClient,
      @NonNull AddressPicker addressPicker,
      @NonNull ProxyAuthenticationGenerator generator,
      @NonNull MetricRegistry metricRegistry) {
    this.netClient = netClient;
    this.addressPicker = addressPicker;
    this.proxyStreamPrefixVisitor =
        new ProxyStreamPrefixVisitor<>(
            vertx, new MetricPipeFactory(metricRegistry, StreamType.HTTPS_DOWN));
    this.httpsConnectEncoder = new HttpsConnectEncoder(generator);
    this.pipeFactory = new MetricPipeFactory(metricRegistry, StreamType.HTTPS_UP);
  }

  private void handleSocketConnected(
      NetSocket netSocket, HttpServerRequest serverRequest, Handler<Throwable> errorHandler) {
    HttpProto.ConnectRequest connectRequest = httpsConnectEncoder.apply(serverRequest);
    netSocket.write(
        Prefix.serializeToBuffer(connectRequest),
        ar -> {
          if (ar.failed()) {
            errorHandler.handle(ar.cause());
            netSocket.close();
          }
        });
    proxyStreamPrefixVisitor.visit(
        netSocket,
        ar -> {
          if (ar.succeeded()) {
            openTunnel(ar.result(), netSocket, serverRequest, errorHandler);
          } else {
            errorHandler.handle(ar.cause());
            netSocket.close();
          }
        });
  }

  private void openTunnel(
      PrefixAndAction<? super NetSocket> prefixAndAction,
      NetSocket netSocket,
      HttpServerRequest serverRequest,
      Handler<Throwable> errorHandler) {
    HttpProto.ConnectResult connectResult;
    try (ByteBufInputStream inputStream =
        new ByteBufInputStream(prefixAndAction.get().getByteBuf())) {
      connectResult = HttpProto.ConnectResult.parseFrom(inputStream);
    } catch (IOException e) {
      errorHandler.handle(e);
      netSocket.close();
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug("{} :{}{}", Constants.leftArrow(), Constants.lineSeparator(), connectResult);
    }
    if (connectResult.getCode() != HttpResponseStatus.OK.code()) {
      serverRequest.response().setStatusCode(connectResult.getCode()).end(connectResult.getMsg());
      netSocket.close();
      return;
    }
    try {
      serverRequest.toNetSocket(
          ar -> {
            if (ar.succeeded()) {
              NetSocket clientSocket = ar.result();
              Utils.exchangeCloseHook(clientSocket, netSocket);
              prefixAndAction.apply(clientSocket);
              pipeFactory.newPipe(clientSocket).to(netSocket);
            } else {
              errorHandler.handle(ar.cause());
              netSocket.close();
            }
          });
    } catch (Exception cause) {
      if (cause instanceof NoSuchElementException) {
        return;
      }
      errorHandler.handle(cause);
      netSocket.close();
    }
  }

  @Override
  public void handle(HttpServerRequest serverRequest) {
    if (serverRequest.method() != HttpMethod.CONNECT
        || StringUtils.isEmpty(serverRequest.getHeader(HttpHeaderNames.HOST))) {
      serverRequest.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
      return;
    }
    SocketAddress address = addressPicker.apply(serverRequest);
    Handler<Throwable> errorHandler = HttpRequestUtils.createErrorHandler(serverRequest.response());
    serverRequest.pause();
    netClient.connect(
        address,
        ar -> {
          if (ar.succeeded()) {
            handleSocketConnected(ar.result(), serverRequest, errorHandler);
          } else {
            errorHandler.handle(ar.cause());
          }
        });
  }
}
