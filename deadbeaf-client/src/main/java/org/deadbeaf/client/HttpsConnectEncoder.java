package org.deadbeaf.client;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import lombok.NonNull;
import org.deadbeaf.auth.ProxyAuthenticationGenerator;
import org.deadbeaf.protocol.HttpHeaderEncoder;
import org.deadbeaf.protocol.HttpProto;
import org.deadbeaf.route.AddressPicker;

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
