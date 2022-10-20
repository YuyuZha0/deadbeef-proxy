package org.deadbeaf.server;

import com.google.common.base.Throwables;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.impl.PipeImpl;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.auth.ProxyAuthenticationValidator;
import org.deadbeaf.protocol.HttpProto;
import org.deadbeaf.protocol.Prefix;
import org.deadbeaf.protocol.PrefixAndAction;
import org.deadbeaf.protocol.ProxyStreamPrefixVisitor;
import org.deadbeaf.util.Utils;

import java.io.IOException;

@Slf4j
public final class Socket2SocketHandler implements Handler<NetSocket> {

  private final ProxyStreamPrefixVisitor<NetSocket> proxyStreamPrefixVisitor;
  private final NetClient netClient;

  private final ProxyAuthenticationValidator proxyAuthenticationValidator;

  public Socket2SocketHandler(
      @NonNull Vertx vertx,
      @NonNull NetClient netClient,
      @NonNull ProxyAuthenticationValidator validator) {
    this.proxyStreamPrefixVisitor = new ProxyStreamPrefixVisitor<>(vertx);
    this.netClient = netClient;
    this.proxyAuthenticationValidator = validator;
  }

  private static Handler<Throwable> createErrorHandler(NetSocket netSocket) {
    return Utils.atMostOnce(
        (Throwable cause) -> {
          String msg;
          if (cause instanceof NoStackTraceThrowable) {
            msg = cause.getMessage();
          } else {
            msg = Throwables.getStackTraceAsString(cause);
          }
          endThenClose(netSocket, HttpResponseStatus.BAD_GATEWAY, msg);
        });
  }

  private static void endThenClose(NetSocket netSocket, HttpResponseStatus status, String msg) {
    HttpProto.ConnectResult connectResult =
        HttpProto.ConnectResult.newBuilder().setCode(status.code()).setMsg(msg).build();
    netSocket.write(
        Prefix.serializeToBuffer(connectResult),
        ar -> {
          netSocket.close();
          if (ar.failed()) {
            log.error("Write response to socket client with exception: ", ar.cause());
          }
        });
  }

  private void handlePrefix(
      NetSocket downSocket,
      PrefixAndAction<? super NetSocket> prefixAndAction,
      Handler<Throwable> errorHandler) {
    HttpProto.ConnectRequest request;
    try (ByteBufInputStream inputStream =
        new ByteBufInputStream(prefixAndAction.get().getByteBuf())) {
      request = HttpProto.ConnectRequest.parseFrom(inputStream);
    } catch (IOException e) {
      errorHandler.handle(e);
      return;
    }
    if (!request.hasAuth() || !proxyAuthenticationValidator.test(request.getAuth())) {
      endThenClose(downSocket, HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED, "No Auth");
      return;
    }
    netClient
        .connect(request.getPort(), request.getHost())
        .onFailure(errorHandler)
        .onSuccess(
            upperSocket -> {
              HttpProto.ConnectResult ok =
                  HttpProto.ConnectResult.newBuilder()
                      .setCode(HttpResponseStatus.OK.code())
                      .build();
              Utils.exchangeCloseHook(upperSocket, downSocket);
              prefixAndAction.accept(
                  upperSocket,
                  ar -> {
                    if (ar.failed()) {
                      downSocket.close();
                      log.error(
                          "Write to {}:{} with exception: ",
                          request.getHost(),
                          request.getPort(),
                          ar.cause());
                    }
                  });
              downSocket.write(
                  Prefix.serializeToBuffer(ok),
                  ar -> {
                    if (ar.succeeded()) {
                      Pipe<Buffer> pipe =
                          new PipeImpl<>(upperSocket).endOnSuccess(false).endOnFailure(false);
                      pipe.to(downSocket);
                    } else {
                      downSocket.close();
                      log.error("Write response to socket client with exception: ", ar.cause());
                    }
                  });
            });
  }

  @Override
  public void handle(NetSocket downSocket) {
    Handler<Throwable> errorHandler = createErrorHandler(downSocket);
    proxyStreamPrefixVisitor.visit(
        downSocket,
        ar -> {
          if (ar.succeeded()) {
            handlePrefix(downSocket, ar.result(), errorHandler);
          } else {
            errorHandler.handle(ar.cause());
          }
        });
  }
}
