# deadbeef-proxy

Vertx http proxy, for some reason I can't talk much.

### System Requirements

`java >= 11`

*if you want to compile the project yourself, you may need `maven <= 3.6.3`*

The project contains some platform dependent components, it is recommended for you to compile the code on you device to
gain better performance.

### How to use?

#### Local

Start a deamon process listening on a port with the following command:

```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -Dio.netty.tryReflectionSetAccessible=true -jar deadbeef-client/target/deadbeef-client-1.0-SNAPSHOT.jar --config client-config.yaml
```

Here is the `client-config.yaml` example:

```yaml
httpPort: 14483
httpsPort: 14484
remoteServer: example.com
localPort: 14482 # this is the local port that you could configure your browser proxy to
secretId: a-secret-id
secretKey: a-secret-key
# options below are optional
preferNativeTransport: true
addressResolver: [ 8.8.8.8, 114.114.114.114 ]
#httpClient:
#  activityLogDataFormat: HEX_DUMP
#  logActivity: false
#  receiveBufferSize: -1
#  reuseAddress: true
#  reusePort: false
#  sendBufferSize: -1
#  trafficClass: -1
#  crlPaths: [ ]
#  crlValues: [ ]
#  enabledCipherSuites: [ ]
#  enabledSecureTransportProtocols: [ TLSv1, TLSv1.1, TLSv1.2 ]
#  idleTimeout: 0
#  idleTimeoutUnit: SECONDS
#  readIdleTimeout: 0
#  soLinger: -1
#  ssl: false
#  sslHandshakeTimeout: 10
#  sslHandshakeTimeoutUnit: SECONDS
#  tcpCork: false
#  tcpFastOpen: false
#  tcpKeepAlive: false
#  tcpNoDelay: true
#  tcpQuickAck: false
#  tcpUserTimeout: 0
#  useAlpn: false
#  writeIdleTimeout: 0
#  connectTimeout: 60000
#  metricsName: ''
#  trustAll: false
#  alpnVersions: [ ]
#  decoderInitialBufferSize: 128
#  defaultHost: localhost
#  defaultPort: 80
#  forceSni: false
#  http2ClearTextUpgrade: true
#  http2ConnectionWindowSize: -1
#  http2KeepAliveTimeout: 60
#  http2MaxPoolSize: 1
#  http2MultiplexingLimit: -1
#  initialSettings: { headerTableSize: 4096, initialWindowSize: 65535, maxConcurrentStreams: 4294967295,
#                     maxFrameSize: 16384, maxHeaderListSize: 8192, pushEnabled: true }
#  keepAlive: true
#  keepAliveTimeout: 60
#  maxChunkSize: 8192
#  maxHeaderSize: 8192
#  maxInitialLineLength: 4096
#  maxPoolSize: 5
#  maxRedirects: 16
#  maxWaitQueueSize: -1
#  maxWebSocketFrameSize: 65536
#  maxWebSocketMessageSize: 262144
#  maxWebSockets: 50
#  name: __vertx.DEFAULT
#  pipelining: false
#  pipeliningLimit: 10
#  poolCleanerPeriod: 1000
#  poolEventLoopSize: 0
#  protocolVersion: HTTP_1_1
#  sendUnmaskedFrames: false
#  shared: false
#  tracingPolicy: PROPAGATE
#  tryUseCompression: false
#  tryUsePerMessageWebSocketCompression: false
#  tryWebSocketDeflateFrameCompression: false
#  verifyHost: true
#  webSocketClosingTimeout: 10
#  webSocketCompressionAllowClientNoContext: false
#  webSocketCompressionLevel: 6
#  webSocketCompressionRequestServerNoContext: false
#netClient:
#  activityLogDataFormat: HEX_DUMP
#  logActivity: false
#  receiveBufferSize: -1
#  reuseAddress: true
#  reusePort: false
#  sendBufferSize: -1
#  trafficClass: -1
#  crlPaths: [ ]
#  crlValues: [ ]
#  enabledCipherSuites: [ ]
#  enabledSecureTransportProtocols: [ TLSv1, TLSv1.1, TLSv1.2 ]
#  idleTimeout: 0
#  idleTimeoutUnit: SECONDS
#  readIdleTimeout: 0
#  soLinger: -1
#  ssl: false
#  sslHandshakeTimeout: 10
#  sslHandshakeTimeoutUnit: SECONDS
#  tcpCork: false
#  tcpFastOpen: false
#  tcpKeepAlive: false
#  tcpNoDelay: true
#  tcpQuickAck: false
#  tcpUserTimeout: 0
#  useAlpn: false
#  writeIdleTimeout: 0
#  connectTimeout: 60000
#  metricsName: ''
#  trustAll: false
#  hostnameVerificationAlgorithm: ''
#  reconnectAttempts: 0
#  reconnectInterval: 1000
#httpServer:
#  activityLogDataFormat: HEX_DUMP
#  logActivity: false
#  receiveBufferSize: -1
#  reuseAddress: true
#  reusePort: false
#  sendBufferSize: -1
#  trafficClass: -1
#  crlPaths: [ ]
#  crlValues: [ ]
#  enabledCipherSuites: [ ]
#  enabledSecureTransportProtocols: [ TLSv1, TLSv1.1, TLSv1.2 ]
#  idleTimeout: 0
#  idleTimeoutUnit: SECONDS
#  readIdleTimeout: 0
#  soLinger: -1
#  ssl: false
#  sslHandshakeTimeout: 10
#  sslHandshakeTimeoutUnit: SECONDS
#  tcpCork: false
#  tcpFastOpen: false
#  tcpKeepAlive: false
#  tcpNoDelay: true
#  tcpQuickAck: false
#  tcpUserTimeout: 0
#  useAlpn: false
#  writeIdleTimeout: 0
#  acceptBacklog: -1
#  clientAuth: NONE
#  host: 0.0.0.0
#  port: 80
#  proxyProtocolTimeout: 10
#  proxyProtocolTimeoutUnit: SECONDS
#  sni: false
#  useProxyProtocol: false
#  acceptUnmaskedFrames: false
#  alpnVersions: [ HTTP_2, HTTP_1_1 ]
#  compressionLevel: 6
#  compressionSupported: false
#  decoderInitialBufferSize: 128
#  decompressionSupported: false
#  handle100ContinueAutomatically: false
#  http2ConnectionWindowSize: -1
#  initialSettings: { headerTableSize: 4096, initialWindowSize: 65535, maxConcurrentStreams: 100,
#                     maxFrameSize: 16384, maxHeaderListSize: 8192, pushEnabled: true }
#  maxChunkSize: 8192
#  maxFormAttributeSize: 8192
#  maxHeaderSize: 8192
#  maxInitialLineLength: 4096
#  maxWebSocketFrameSize: 65536
#  maxWebSocketMessageSize: 262144
#  perFrameWebSocketCompressionSupported: true
#  perMessageWebSocketCompressionSupported: true
#  tracingPolicy: ALWAYS
#  webSocketAllowServerNoContext: false
#  webSocketClosingTimeout: 10
#  webSocketCompressionLevel: 6
#  webSocketPreferredClientNoContext: false
```

### Remote

Start a deamon process on your remote machine to handle requests from you local proxy server.

*Note: DEADBEEF-SERVER handles HTTP/HTTPS on different ports, configure you firewall properly*

You may start with the following command:

```bash
#!/bin/bash
nohup java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -Dio.netty.tryReflectionSetAccessible=true -jar deadbeef-server/target/deadbeef-server-1.0-SNAPSHOT.jar -c server-config.yaml > run.log 2>&1 & echo $! > pid.file
```

Here is what `server-config.yaml` should be like:

```yaml
httpPort: 14483
httpsPort: 14484
auth:
  - { secretKey: one-secret-key, secretId: one-secret-id }
  - { secretKey: another-secret-key, secretId: another-secret-id }
preferNativeTransport: true
addressResolver: [ 8.8.8.8, 114.114.114.114 ]
#httpClient:
#  activityLogDataFormat: HEX_DUMP
#  logActivity: false
#  receiveBufferSize: -1
#  reuseAddress: true
#  reusePort: false
#  sendBufferSize: -1
#  trafficClass: -1
#  crlPaths: [ ]
#  crlValues: [ ]
#  enabledCipherSuites: [ ]
#  enabledSecureTransportProtocols: [ TLSv1, TLSv1.1, TLSv1.2 ]
#  idleTimeout: 0
#  idleTimeoutUnit: SECONDS
#  readIdleTimeout: 0
#  soLinger: -1
#  ssl: false
#  sslHandshakeTimeout: 10
#  sslHandshakeTimeoutUnit: SECONDS
#  tcpCork: false
#  tcpFastOpen: false
#  tcpKeepAlive: false
#  tcpNoDelay: true
#  tcpQuickAck: false
#  tcpUserTimeout: 0
#  useAlpn: false
#  writeIdleTimeout: 0
#  connectTimeout: 60000
#  metricsName: ''
#  trustAll: false
#  alpnVersions: [ ]
#  decoderInitialBufferSize: 128
#  defaultHost: localhost
#  defaultPort: 80
#  forceSni: false
#  http2ClearTextUpgrade: true
#  http2ConnectionWindowSize: -1
#  http2KeepAliveTimeout: 60
#  http2MaxPoolSize: 1
#  http2MultiplexingLimit: -1
#  initialSettings: { headerTableSize: 4096, initialWindowSize: 65535, maxConcurrentStreams: 4294967295,
#                     maxFrameSize: 16384, maxHeaderListSize: 8192, pushEnabled: true }
#  keepAlive: true
#  keepAliveTimeout: 60
#  maxChunkSize: 8192
#  maxHeaderSize: 8192
#  maxInitialLineLength: 4096
#  maxPoolSize: 5
#  maxRedirects: 16
#  maxWaitQueueSize: -1
#  maxWebSocketFrameSize: 65536
#  maxWebSocketMessageSize: 262144
#  maxWebSockets: 50
#  name: __vertx.DEFAULT
#  pipelining: false
#  pipeliningLimit: 10
#  poolCleanerPeriod: 1000
#  poolEventLoopSize: 0
#  protocolVersion: HTTP_1_1
#  sendUnmaskedFrames: false
#  shared: false
#  tracingPolicy: PROPAGATE
#  tryUseCompression: false
#  tryUsePerMessageWebSocketCompression: false
#  tryWebSocketDeflateFrameCompression: false
#  verifyHost: true
#  webSocketClosingTimeout: 10
#  webSocketCompressionAllowClientNoContext: false
#  webSocketCompressionLevel: 6
#  webSocketCompressionRequestServerNoContext: false
#httpServer:
#  activityLogDataFormat: HEX_DUMP
#  logActivity: false
#  receiveBufferSize: -1
#  reuseAddress: true
#  reusePort: false
#  sendBufferSize: -1
#  trafficClass: -1
#  crlPaths: [ ]
#  crlValues: [ ]
#  enabledCipherSuites: [ ]
#  enabledSecureTransportProtocols: [ TLSv1, TLSv1.1, TLSv1.2 ]
#  idleTimeout: 0
#  idleTimeoutUnit: SECONDS
#  readIdleTimeout: 0
#  soLinger: -1
#  ssl: false
#  sslHandshakeTimeout: 10
#  sslHandshakeTimeoutUnit: SECONDS
#  tcpCork: false
#  tcpFastOpen: false
#  tcpKeepAlive: false
#  tcpNoDelay: true
#  tcpQuickAck: false
#  tcpUserTimeout: 0
#  useAlpn: false
#  writeIdleTimeout: 0
#  acceptBacklog: -1
#  clientAuth: NONE
#  host: 0.0.0.0
#  port: 80
#  proxyProtocolTimeout: 10
#  proxyProtocolTimeoutUnit: SECONDS
#  sni: false
#  useProxyProtocol: false
#  acceptUnmaskedFrames: false
#  alpnVersions: [ HTTP_2, HTTP_1_1 ]
#  compressionLevel: 6
#  compressionSupported: false
#  decoderInitialBufferSize: 128
#  decompressionSupported: false
#  handle100ContinueAutomatically: false
#  http2ConnectionWindowSize: -1
#  initialSettings: { headerTableSize: 4096, initialWindowSize: 65535, maxConcurrentStreams: 100,
#                     maxFrameSize: 16384, maxHeaderListSize: 8192, pushEnabled: true }
#  maxChunkSize: 8192
#  maxFormAttributeSize: 8192
#  maxHeaderSize: 8192
#  maxInitialLineLength: 4096
#  maxWebSocketFrameSize: 65536
#  maxWebSocketMessageSize: 262144
#  perFrameWebSocketCompressionSupported: true
#  perMessageWebSocketCompressionSupported: true
#  tracingPolicy: ALWAYS
#  webSocketAllowServerNoContext: false
#  webSocketClosingTimeout: 10
#  webSocketCompressionLevel: 6
#  webSocketPreferredClientNoContext: false
#httpsClient:
#  activityLogDataFormat: HEX_DUMP
#  logActivity: false
#  receiveBufferSize: -1
#  reuseAddress: true
#  reusePort: false
#  sendBufferSize: -1
#  trafficClass: -1
#  crlPaths: [ ]
#  crlValues: [ ]
#  enabledCipherSuites: [ ]
#  enabledSecureTransportProtocols: [ TLSv1, TLSv1.1, TLSv1.2 ]
#  idleTimeout: 0
#  idleTimeoutUnit: SECONDS
#  readIdleTimeout: 0
#  soLinger: -1
#  ssl: false
#  sslHandshakeTimeout: 10
#  sslHandshakeTimeoutUnit: SECONDS
#  tcpCork: false
#  tcpFastOpen: false
#  tcpKeepAlive: false
#  tcpNoDelay: true
#  tcpQuickAck: false
#  tcpUserTimeout: 0
#  useAlpn: false
#  writeIdleTimeout: 0
#  connectTimeout: 60000
#  metricsName: ''
#  trustAll: false
#  hostnameVerificationAlgorithm: ''
#  reconnectAttempts: 0
#  reconnectInterval: 1000
#httpsServer:
#  activityLogDataFormat: HEX_DUMP
#  logActivity: false
#  receiveBufferSize: -1
#  reuseAddress: true
#  reusePort: false
#  sendBufferSize: -1
#  trafficClass: -1
#  crlPaths: [ ]
#  crlValues: [ ]
#  enabledCipherSuites: [ ]
#  enabledSecureTransportProtocols: [ TLSv1, TLSv1.1, TLSv1.2 ]
#  idleTimeout: 0
#  idleTimeoutUnit: SECONDS
#  readIdleTimeout: 0
#  soLinger: -1
#  ssl: false
#  sslHandshakeTimeout: 10
#  sslHandshakeTimeoutUnit: SECONDS
#  tcpCork: false
#  tcpFastOpen: false
#  tcpKeepAlive: false
#  tcpNoDelay: true
#  tcpQuickAck: false
#  tcpUserTimeout: 0
#  useAlpn: false
#  writeIdleTimeout: 0
#  acceptBacklog: -1
#  clientAuth: NONE
#  host: 0.0.0.0
#  port: 0
#  proxyProtocolTimeout: 10
#  proxyProtocolTimeoutUnit: SECONDS
#  sni: false
#  useProxyProtocol: false
```
