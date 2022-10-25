package org.deadbeef.client;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;
import org.deadbeef.auth.ProxyAuthenticationGenerator;
import org.deadbeef.protocol.HttpProto;
import org.deadbeef.route.AddressPicker;
import org.deadbeef.util.HttpHeaderEncoder;

import java.util.function.Function;

public final class HttpsConnectEncoder
    implements Function<HttpServerRequest, HttpProto.ConnectRequest> {

  private final AddressPicker addressPicker = AddressPicker.ofHostHeader(443);
  private final HttpHeaderEncoder headerEncoder = new HttpHeaderEncoder();
  private final ProxyAuthenticationGenerator proxyAuthenticationGenerator;

  public HttpsConnectEncoder(@NonNull ProxyAuthenticationGenerator proxyAuthenticationGenerator) {
    this.proxyAuthenticationGenerator = proxyAuthenticationGenerator;
  }

  @Override
  public HttpProto.ConnectRequest apply(HttpServerRequest serverRequest) {
    SocketAddress socketAddress = addressPicker.apply(serverRequest);
    return HttpProto.ConnectRequest.newBuilder()
        .setHeaders(headerEncoder.apply(serverRequest.headers()))
        .setAuth(proxyAuthenticationGenerator.get())
        .setHost(socketAddress.host())
        .setPort(socketAddress.port())
        .build();
  }
}
