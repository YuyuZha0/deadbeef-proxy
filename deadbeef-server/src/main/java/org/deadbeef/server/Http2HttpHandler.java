package org.deadbeef.server;

import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.deadbeef.auth.ProxyAuthenticationValidator;
import org.deadbeef.protocol.HttpProto;
import org.deadbeef.streams.PipeFactory;
import org.deadbeef.streams.Prefix;
import org.deadbeef.streams.PrefixAndAction;
import org.deadbeef.streams.ProxyStreamPrefixVisitor;
import org.deadbeef.util.Constants;
import org.deadbeef.util.HttpHeaderDecoder;
import org.deadbeef.util.HttpRequestUtils;

import java.io.IOException;

@Slf4j
public final class Http2HttpHandler implements Handler<HttpServerRequest> {

  private final HttpClientResponseEncoder encoder = new HttpClientResponseEncoder();
  private final HttpHeaderDecoder headerDecoder = new HttpHeaderDecoder();
  private final PipeFactory pipeFactory;
  private final HttpClient httpClient;
  private final ProxyStreamPrefixVisitor<HttpClientRequest> proxyStreamPrefixVisitor;

  private final ProxyAuthenticationValidator proxyAuthenticationValidator;

  public Http2HttpHandler(
      @NonNull Vertx vertx,
      @NonNull HttpClient httpClient,
      @NonNull ProxyAuthenticationValidator validator,
      @NonNull PipeFactory pipeFactory) {
    this.httpClient = httpClient;
    this.proxyStreamPrefixVisitor = new ProxyStreamPrefixVisitor<>(vertx, pipeFactory);
    this.proxyAuthenticationValidator = validator;
    this.pipeFactory = pipeFactory;
  }

  @Override
  public void handle(HttpServerRequest serverRequest) {
    HttpServerResponse serverResponse = serverRequest.response();
    if (serverRequest.method() != HttpMethod.POST) {
      serverResponse.setStatusCode(HttpResponseStatus.METHOD_NOT_ALLOWED.code()).end();
      return;
    }
    if (!proxyAuthenticationValidator.testString(
        serverRequest.getHeader(Constants.authHeaderName()))) {
      serverResponse.setStatusCode(HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED.code()).end();
      return;
    }
    Handler<Throwable> errorHandler = HttpRequestUtils.createErrorHandler(serverResponse);
    proxyStreamPrefixVisitor
        .visit(serverRequest)
        .onFailure(errorHandler)
        .onSuccess(
            prefixAndAction -> {
              HttpProto.Request request;
              try (ByteBufInputStream inputStream =
                  new ByteBufInputStream(prefixAndAction.get().getByteBuf())) {
                request = HttpProto.Request.parseFrom(inputStream);
              } catch (IOException e) {
                errorHandler.handle(e);
                return;
              }
              if (log.isDebugEnabled()) {
                log.debug("{} :{}{}", Constants.rightArrow(), Constants.lineSeparator(), request);
              }
              httpClient.request(
                  buildRequestOptions(request),
                  ar -> {
                    if (ar.succeeded()) {
                      onRequestSuccess(ar.result(), prefixAndAction, serverResponse, errorHandler);
                    } else {
                      errorHandler.handle(ar.cause());
                    }
                  });
            });
  }

  private void onRequestSuccess(
      HttpClientRequest clientRequest,
      PrefixAndAction<? super HttpClientRequest> prefixAndAction,
      HttpServerResponse serverResponse,
      Handler<Throwable> errorHandler) {
    clientRequest.exceptionHandler(errorHandler);
    prefixAndAction.accept(
        clientRequest,
        ar -> {
          clientRequest.end();
          if (ar.succeeded()) {
            clientRequest
                .response()
                .onSuccess(
                    clientResponse -> writeResponse(serverResponse, clientResponse, errorHandler))
                .onFailure(errorHandler);
          } else {
            errorHandler.handle(ar.cause());
          }
        });
  }

  private void writeResponse(
      HttpServerResponse serverResponse,
      HttpClientResponse clientResponse,
      Handler<Throwable> errorHandler) {
    long contentLength = HttpRequestUtils.contentLength(clientResponse.headers());
    HttpProto.Response proto = encoder.apply(clientResponse);
    if (log.isDebugEnabled()) {
      log.debug("{} :{}{}", Constants.leftArrow(), Constants.lineSeparator(), proto);
    }
    putHeaders(proto, contentLength, serverResponse);
    Buffer prefixData = Prefix.serializeToBuffer(proto);
    if (contentLength == 0L) {
      clientResponse.end();
      serverResponse.end(prefixData);
      return;
    }
    serverResponse.write(prefixData).onFailure(errorHandler);
    pipeFactory
        .newPipe(clientResponse)
        .to(serverResponse)
        .onSuccess(
            v -> {
              if (log.isDebugEnabled()) {
                log.debug(
                    "Server response ended={}, contentLength={}, prefixLength={}, bytesWritten={}",
                    serverResponse.ended(),
                    contentLength,
                    prefixData.length(),
                    serverResponse.bytesWritten());
              }
              serverResponse.end();
            })
        .onFailure(errorHandler);
  }

  private void putHeaders(
      HttpProto.Response response, long contentLength, HttpServerResponse serverResponse) {
    serverResponse.putHeader(
        HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
    if (contentLength >= 0) {
      serverResponse.putHeader(
          HttpHeaderNames.CONTENT_LENGTH,
          Long.toString(Prefix.serializeToBufferSize(response) + contentLength));
    }
  }

  private RequestOptions buildRequestOptions(HttpProto.Request request) {
    RequestOptions requestOptions = new RequestOptions();
    if (request.hasHeaders()) {
      requestOptions.setHeaders(headerDecoder.apply(request.getHeaders()));
    }
    requestOptions.setAbsoluteURI(request.getAbsoluteUri());
    requestOptions.setMethod(HttpMethod.valueOf(request.getMethod().name()));
    requestOptions.setTimeout(Constants.requestTimeout());
    return requestOptions;
  }
}
