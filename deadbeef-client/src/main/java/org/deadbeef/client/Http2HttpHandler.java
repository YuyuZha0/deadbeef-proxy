package org.deadbeef.client;

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
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.protocol.HttpProto;
import org.deadbeef.route.AddressPicker;
import org.deadbeef.streams.Prefix;
import org.deadbeef.streams.ProxyStreamPrefixVisitor;
import org.deadbeef.util.Constants;
import org.deadbeef.util.HttpHeaderDecoder;
import org.deadbeef.util.HttpRequestUtils;

import java.io.IOException;

@Slf4j
public final class Http2HttpHandler implements Handler<HttpServerRequest> {

  private final HttpServerRequestEncoder httpServerRequestEncoder = new HttpServerRequestEncoder();
  private final HttpHeaderDecoder headerDecoder = new HttpHeaderDecoder();

  private final ProxyStreamPrefixVisitor<HttpServerResponse> proxyStreamPrefixVisitor;
  private final HttpClient httpClient;
  private final AddressPicker addressPicker;

  private final ProxyAuthenticationGenerator proxyAuthenticationGenerator;

  public Http2HttpHandler(
      @NonNull Vertx vertx,
      @NonNull HttpClient httpClient,
      @NonNull AddressPicker addressPicker,
      @NonNull ProxyAuthenticationGenerator generator) {
    this.proxyStreamPrefixVisitor = new ProxyStreamPrefixVisitor<>(vertx);
    this.httpClient = httpClient;
    this.addressPicker = addressPicker;
    this.proxyAuthenticationGenerator = generator;
  }

  private boolean should100Continue(HttpServerRequest request) {
    // handle expectations
    // https://httpwg.org/specs/rfc7231.html#header.expect
    String expect = request.getHeader(HttpHeaderNames.EXPECT);
    if (StringUtils.isNotEmpty(expect)) {
      // requirements validation
      if (expect.equalsIgnoreCase("100-continue")) {
        // A server that receives a 100-continue expectation in an HTTP/1.0 request MUST ignore that
        // expectation.
        if (request.version() != HttpVersion.HTTP_1_0) {
          // signal the client to continue
          request.response().writeContinue();
        }
      } else {
        // the server cannot meet the expectation, we only know about 100-continue
        request.response().setStatusCode(HttpResponseStatus.EXPECTATION_FAILED.code()).end();
        return false;
      }
    }
    return true;
  }

  @Override
  public void handle(HttpServerRequest serverRequest) {
    if (!should100Continue(serverRequest)) {
      return;
    }
    long contentLength = HttpRequestUtils.contentLength(serverRequest.headers());
    HttpProto.Request proto = httpServerRequestEncoder.apply(serverRequest);
    if (log.isDebugEnabled()) {
      log.debug("{} :{}{}", Constants.rightArrow(), Constants.lineSeparator(), proto);
    }

    RequestOptions requestOptions = new RequestOptions();
    requestOptions.setMethod(HttpMethod.POST);
    requestOptions.setServer(addressPicker.apply(serverRequest));
    requestOptions.setTimeout(Constants.requestTimeout());
    putHeaders(proto, contentLength, requestOptions);

    serverRequest.pause();
    HttpServerResponse serverResponse = serverRequest.response();
    Handler<Throwable> errorHandler = HttpRequestUtils.createErrorHandler(serverResponse, log);
    httpClient
        .request(requestOptions)
        .onSuccess(
            clientRequest -> {
              clientRequest.exceptionHandler(errorHandler);
              Buffer prefixData = Prefix.serializeToBuffer(proto);
              // Only exact 0 means empty body!
              if (contentLength == 0) {
                clientRequest
                    .end(prefixData)
                    .onSuccess(
                        v -> awaitUpperStreamResponse(serverResponse, clientRequest, errorHandler))
                    .onFailure(errorHandler);
                return;
              }
              clientRequest.write(Prefix.serializeToBuffer(proto));
              serverRequest
                  .pipeTo(clientRequest)
                  .onSuccess(
                      v -> awaitUpperStreamResponse(serverResponse, clientRequest, errorHandler))
                  .onFailure(errorHandler);
              serverRequest.resume();
            })
        .onFailure(errorHandler);
  }

  private void awaitUpperStreamResponse(
      HttpServerResponse serverResponse,
      HttpClientRequest clientRequest,
      Handler<Throwable> errorHandler) {
    clientRequest.response(
        responseResult -> {
          if (responseResult.succeeded()) {
            handleProxyServerResponse(serverResponse, responseResult.result(), errorHandler);
          } else {
            errorHandler.handle(responseResult.cause());
          }
        });
  }

  private void handleProxyServerResponse(
      HttpServerResponse serverResponse,
      HttpClientResponse clientResponse,
      Handler<Throwable> errorHandler) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Proxy server response headers:{}{}",
          Constants.lineSeparator(),
          clientResponse.headers());
    }
    if (clientResponse.statusCode() != HttpResponseStatus.OK.code()) {
      serverResponse
          .setStatusCode(clientResponse.statusCode())
          .setStatusMessage(clientResponse.statusMessage());
      clientResponse.headers().forEach(serverResponse::putHeader);
      serverResponse.end();
      return;
    }
    proxyStreamPrefixVisitor
        .visit(clientResponse)
        .onFailure(errorHandler)
        .onSuccess(
            prefixAndAction -> {
              HttpProto.Response response;
              try (ByteBufInputStream inputStream =
                  new ByteBufInputStream(prefixAndAction.get().getByteBuf())) {
                response = HttpProto.Response.parseFrom(inputStream);
              } catch (IOException e) {
                errorHandler.handle(e);
                return;
              }
              if (log.isDebugEnabled()) {
                log.debug("{} :{}{}", Constants.leftArrow(), Constants.lineSeparator(), response);
              }
              setupResponse(serverResponse, response);
              prefixAndAction.accept(
                  serverResponse,
                  ar -> {
                    if (ar.succeeded()) {
                      serverResponse.end();
                    } else {
                      errorHandler.handle(ar.cause());
                    }
                  });
            });
  }

  private void setupResponse(HttpServerResponse response, HttpProto.Response proto) {
    if (proto.hasHeaders()) {
      headerDecoder.visit(proto.getHeaders(), response::putHeader);
    }
    if (proto.hasStatusCode()) {
      response.setStatusCode(proto.getStatusCode());
    }
    if (proto.hasStatusMessage()) {
      response.setStatusMessage(proto.getStatusMessage());
    }
  }

  private void putHeaders(
      HttpProto.Request request, long contentLength, RequestOptions requestOptions) {
    requestOptions.putHeader(
        HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
    if (contentLength >= 0) {
      requestOptions.putHeader(
          HttpHeaderNames.CONTENT_LENGTH,
          Long.toString(Prefix.serializeToBufferSize(request) + contentLength));
    }
    requestOptions.putHeader(Constants.authHeaderName(), proxyAuthenticationGenerator.getString());
  }
}
