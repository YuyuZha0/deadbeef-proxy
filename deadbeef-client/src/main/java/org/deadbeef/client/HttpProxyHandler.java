package org.deadbeef.client;

import com.codahale.metrics.Timer;
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
import io.vertx.core.net.SocketAddress;
import java.io.IOException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.metrics.ProxyMetrics;
import org.deadbeef.protocol.HttpProto;
import org.deadbeef.route.HostNameMatcher;
import org.deadbeef.route.OriginProvider;
import org.deadbeef.streams.MetricPipeFactory;
import org.deadbeef.streams.PipeFactory;
import org.deadbeef.streams.Prefix;
import org.deadbeef.streams.ProxyStreamPrefixVisitor;
import org.deadbeef.util.Constants;
import org.deadbeef.util.HopByHopHeaders;
import org.deadbeef.util.HttpHeaderDecoder;
import org.deadbeef.util.HttpRequestUtils;
import org.deadbeef.util.Utils;

/**
 * Handles plaintext HTTP proxy requests. Unless {@code proxyAll} is set, it first tries an ordinary
 * HTTP request straight to the target (gated by {@link ReachabilityGate}); only when that direct
 * connection cannot be established does it fall back to the remote proxy, which wraps the request
 * in the protobuf envelope. Fallback happens at connection time — before the request body is
 * consumed — so the body is intact for whichever path wins.
 */
@Slf4j
public final class HttpProxyHandler implements Handler<HttpServerRequest> {

  private final HttpServerRequestEncoder httpServerRequestEncoder = new HttpServerRequestEncoder();
  private final HttpHeaderDecoder headerDecoder = new HttpHeaderDecoder();

  private final PipeFactory pipeFactory;
  private final PipeFactory downPipeFactory;

  private final ProxyStreamPrefixVisitor<HttpServerResponse> proxyStreamPrefixVisitor;
  private final HttpClient httpClient;
  private final OriginProvider remoteProvider;
  private final OriginProvider targetProvider;
  private final ReachabilityGate<HttpClientRequest> reachabilityGate;
  private final HostNameMatcher localOnly;
  private final HostNameMatcher remoteOnly;
  private final boolean proxyAll;

  private final ProxyAuthenticationGenerator proxyAuthenticationGenerator;
  private final ProxyMetrics metrics;

  public HttpProxyHandler(
      @NonNull Vertx vertx,
      @NonNull HttpClient httpClient,
      @NonNull OriginProvider remoteProvider,
      @NonNull OriginProvider targetProvider,
      @NonNull ReachabilityGate<HttpClientRequest> reachabilityGate,
      @NonNull HostNameMatcher localOnly,
      @NonNull HostNameMatcher remoteOnly,
      boolean proxyAll,
      @NonNull ProxyAuthenticationGenerator generator,
      @NonNull ProxyMetrics metrics) {
    this.proxyStreamPrefixVisitor =
        new ProxyStreamPrefixVisitor<>(vertx, new MetricPipeFactory(metrics.httpBytesDown));
    this.httpClient = httpClient;
    this.remoteProvider = remoteProvider;
    this.targetProvider = targetProvider;
    this.reachabilityGate = reachabilityGate;
    this.localOnly = localOnly;
    this.remoteOnly = remoteOnly;
    this.proxyAll = proxyAll;
    this.proxyAuthenticationGenerator = generator;
    this.metrics = metrics;
    this.pipeFactory = new MetricPipeFactory(metrics.httpBytesUp, true, true);
    this.downPipeFactory = new MetricPipeFactory(metrics.httpBytesDown);
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
    metrics.httpRequestsTotal.inc();
    metrics.httpInFlightInc();
    Timer.Context timerCtx = metrics.httpRequestDuration.time();
    Handler<Void> finishHandler =
        Utils.atMostOnce(
            v -> {
              timerCtx.stop();
              metrics.httpInFlightDec();
            });

    serverRequest.pause();
    HttpServerResponse serverResponse = serverRequest.response();
    Handler<Throwable> originalErrorHandler = HttpRequestUtils.createErrorHandler(serverResponse);
    Handler<Throwable> errorHandler =
        Utils.atMostOnce(
            cause -> {
              metrics.httpRequestsFailed.inc();
              finishHandler.handle(null);
              originalErrorHandler.handle(cause);
            });

    serverResponse.closeHandler(finishHandler);
    serverResponse.endHandler(finishHandler);

    long contentLength = HttpRequestUtils.contentLength(serverRequest.headers());

    if (proxyAll) {
      proxyToRemote(serverRequest, serverResponse, contentLength, errorHandler);
      return;
    }

    SocketAddress target;
    try {
      target = targetProvider.apply(serverRequest);
    } catch (RuntimeException e) {
      // Cannot determine a direct target — let the remote proxy handle it.
      proxyToRemote(serverRequest, serverResponse, contentLength, errorHandler);
      return;
    }

    String host = target.host();
    if (remoteOnly.match(host)) {
      // Known-blocked: skip the doomed direct attempt.
      proxyToRemote(serverRequest, serverResponse, contentLength, errorHandler);
      return;
    }

    RequestOptions directOptions = buildDirectOptions(serverRequest, target);
    if (localOnly.match(host)) {
      // Hard-pinned direct: never use the remote proxy; a connect failure surfaces as an error.
      httpClient
          .request(directOptions)
          .onSuccess(
              clientRequest ->
                  proxyDirect(
                      serverRequest, serverResponse, clientRequest, contentLength, errorHandler))
          .onFailure(errorHandler);
      return;
    }

    // Unlisted: try direct first; fall back to the remote proxy on connect failure.
    reachabilityGate
        .apply(target, () -> httpClient.request(directOptions))
        .onSuccess(
            clientRequest ->
                proxyDirect(
                    serverRequest, serverResponse, clientRequest, contentLength, errorHandler))
        .onFailure(
            cause -> proxyToRemote(serverRequest, serverResponse, contentLength, errorHandler));
  }

  // ---- direct path: ordinary HTTP straight to the target ----

  private RequestOptions buildDirectOptions(HttpServerRequest serverRequest, SocketAddress target) {
    RequestOptions options = new RequestOptions();
    options.setMethod(serverRequest.method());
    options.setAbsoluteURI(HttpServerRequestEncoder.absoluteUrl(serverRequest));
    options.setHeaders(HopByHopHeaders.copyEndToEnd(serverRequest.headers()));
    // Pin the TCP target; the absolute URI still drives the request line / Host header.
    options.setServer(target);
    return options;
  }

  private void proxyDirect(
      HttpServerRequest serverRequest,
      HttpServerResponse serverResponse,
      HttpClientRequest clientRequest,
      long contentLength,
      Handler<Throwable> errorHandler) {
    metrics.httpRequestsDirect.inc();
    clientRequest.exceptionHandler(errorHandler);
    if (contentLength < 0) {
      clientRequest.setChunked(true);
    }
    if (contentLength == 0) {
      clientRequest
          .end()
          .onSuccess(v -> awaitDirectResponse(serverResponse, clientRequest, errorHandler))
          .onFailure(errorHandler);
      return;
    }
    pipeFactory
        .newPipe(serverRequest)
        .to(clientRequest)
        .onSuccess(v -> awaitDirectResponse(serverResponse, clientRequest, errorHandler))
        .onFailure(errorHandler);
  }

  private void awaitDirectResponse(
      HttpServerResponse serverResponse,
      HttpClientRequest clientRequest,
      Handler<Throwable> errorHandler) {
    clientRequest
        .response()
        .onSuccess(
            clientResponse -> writeDirectResponse(serverResponse, clientResponse, errorHandler))
        .onFailure(errorHandler);
  }

  private void writeDirectResponse(
      HttpServerResponse serverResponse,
      HttpClientResponse clientResponse,
      Handler<Throwable> errorHandler) {
    int status = clientResponse.statusCode();
    metrics.recordHttpStatus(status);
    serverResponse.setStatusCode(status).setStatusMessage(clientResponse.statusMessage());
    HopByHopHeaders.forEachEndToEnd(clientResponse.headers(), serverResponse::putHeader);
    long contentLength = HttpRequestUtils.contentLength(clientResponse.headers());
    if (contentLength == 0) {
      serverResponse.end();
      return;
    }
    if (contentLength < 0) {
      serverResponse.setChunked(true);
    }
    downPipeFactory
        .newPipe(clientResponse)
        .to(serverResponse)
        .onSuccess(v -> serverResponse.end())
        .onFailure(errorHandler);
  }

  // ---- remote path: protobuf-wrapped request to the remote proxy ----

  private void proxyToRemote(
      HttpServerRequest serverRequest,
      HttpServerResponse serverResponse,
      long contentLength,
      Handler<Throwable> errorHandler) {
    metrics.httpRequestsRemote.inc();
    HttpProto.Request proto = httpServerRequestEncoder.apply(serverRequest);
    if (log.isDebugEnabled()) {
      log.debug("{} :{}{}", Constants.rightArrow(), Constants.lineSeparator(), proto);
    }

    RequestOptions requestOptions = new RequestOptions();
    requestOptions.setMethod(HttpMethod.POST);
    requestOptions.setServer(remoteProvider.apply(serverRequest));
    putHeaders(proto, contentLength, requestOptions);

    httpClient
        .request(requestOptions)
        .onSuccess(
            clientRequest -> {
              clientRequest.exceptionHandler(errorHandler);
              if (contentLength < 0) {
                // Unknown / chunked upstream body — request chunked transfer-encoding outbound.
                clientRequest.setChunked(true);
              }
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
              clientRequest.write(prefixData);
              pipeFactory
                  .newPipe(serverRequest)
                  .to(clientRequest)
                  .onSuccess(
                      v -> awaitUpperStreamResponse(serverResponse, clientRequest, errorHandler))
                  .onFailure(errorHandler);
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
      // The remote proxy itself rejected — surface its status to the browser. Count as failure
      // (not as a bucketed browser-facing status, since the upstream's status is the real signal).
      metrics.httpRequestsFailed.inc();
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
              if (response.hasStatusCode()) {
                metrics.recordHttpStatus(response.getStatusCode());
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
