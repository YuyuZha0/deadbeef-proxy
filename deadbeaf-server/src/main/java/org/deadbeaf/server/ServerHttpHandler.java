package org.deadbeaf.server;

import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import lombok.extern.slf4j.Slf4j;
import org.deadbeaf.protocol.HttpHeaderDecoder;
import org.deadbeaf.protocol.HttpProto;
import org.deadbeaf.protocol.Prefix;
import org.deadbeaf.protocol.ProxyStreamPrefixResolver;
import org.deadbeaf.util.Utils;

import java.io.IOException;

@Slf4j
public final class ServerHttpHandler implements Handler<HttpServerRequest> {

  private final HttpClientResponseEncoder encoder = new HttpClientResponseEncoder();
  private final HttpHeaderDecoder headerDecoder = new HttpHeaderDecoder();
  private final HttpClient httpClient;
  private final ProxyStreamPrefixResolver<HttpClientRequest> proxyStreamPrefixResolver;

  public ServerHttpHandler(Vertx vertx, HttpClient httpClient) {
    this.httpClient = httpClient;
    this.proxyStreamPrefixResolver = new ProxyStreamPrefixResolver<>(vertx);
  }

  @Override
  public void handle(HttpServerRequest serverRequest) {
    HttpServerResponse serverResponse = serverRequest.response();
    Handler<Throwable> errorHandler = Utils.createErrorHandler(serverResponse, log);
    proxyStreamPrefixResolver
        .resolvePrefix(
            serverRequest,
            prefix -> {
              HttpProto.Request request;
              try (ByteBufInputStream inputStream = new ByteBufInputStream(prefix.getByteBuf())) {
                request = HttpProto.Request.parseFrom(inputStream);
              } catch (IOException e) {
                return Future.failedFuture(e);
              }
              if (log.isDebugEnabled()) {
                log.debug("{} :{}{}", Utils.rightArrow(), Utils.lineSeparator(), request);
              }
              Promise<HttpClientRequest> promise = Promise.promise();
              httpClient.request(
                  buildRequestOptions(request),
                  ar -> {
                    if (ar.succeeded()) {
                      HttpClientRequest clientRequest = ar.result();
                      Utils.handleClosing(serverRequest, clientRequest);
                      promise.tryComplete(clientRequest);
                    } else {
                      promise.tryFail(ar.cause());
                    }
                  });
              return promise.future();
            },
            clientRequest -> {
              clientRequest.end();
              clientRequest
                  .response()
                  .onSuccess(
                      clientResponse -> writeResponse(serverResponse, clientResponse, errorHandler))
                  .onFailure(errorHandler);
            })
        .onFailure(errorHandler);
  }

  private void writeResponse(
      HttpServerResponse serverResponse,
      HttpClientResponse clientResponse,
      Handler<Throwable> errorHandler) {
    long contentLength = Utils.contentLength(clientResponse.headers());
    HttpProto.Response proto = encoder.apply(clientResponse);
    if (log.isDebugEnabled()) {
      log.debug("{} :{}{}", Utils.leftArrow(), Utils.lineSeparator(), proto);
    }
    putHeaders(proto, contentLength, serverResponse);
    Buffer prefixData = Prefix.serializeToBuffer(proto);
    if (contentLength == 0L) {
      clientResponse.end();
      serverResponse.end(prefixData);
      return;
    }
    serverResponse.write(prefixData).onFailure(errorHandler);
    clientResponse
        .pipeTo(serverResponse)
        .onSuccess(
            v -> {
              if (log.isDebugEnabled()) {
                log.debug(
                    "Pipe stream ended, contentLength={}, prefixLength={}, bytesWritten={}",
                    contentLength,
                    prefixData.length(),
                    serverResponse.bytesWritten());
              }
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
    } else {
      serverResponse.putHeader(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
    }
  }

  private RequestOptions buildRequestOptions(HttpProto.Request request) {
    RequestOptions requestOptions = new RequestOptions();
    if (request.hasHeaders()) {
      requestOptions.setHeaders(headerDecoder.apply(request.getHeaders()));
    }
    requestOptions.setAbsoluteURI(request.getAbsoluteUri());
    requestOptions.setMethod(HttpMethod.valueOf(request.getMethod().name()));
    return requestOptions;
  }
}
