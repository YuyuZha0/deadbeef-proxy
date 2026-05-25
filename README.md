# deadbeef-proxy

```text

 ________  _______   ________  ________  ________  _______   _______   ________ 
|\   ___ \|\  ___ \ |\   __  \|\   ___ \|\   __  \|\  ___ \ |\  ___ \ |\  _____\
\ \  \_|\ \ \   __/|\ \  \|\  \ \  \_|\ \ \  \|\ /\ \   __/|\ \   __/|\ \  \__/ 
 \ \  \ \\ \ \  \_|/_\ \   __  \ \  \ \\ \ \   __  \ \  \_|/_\ \  \_|/_\ \   __\
  \ \  \_\\ \ \  \_|\ \ \  \ \  \ \  \_\\ \ \  \|\  \ \  \_|\ \ \  \_|\ \ \  \_|
   \ \_______\ \_______\ \__\ \__\ \_______\ \_______\ \_______\ \_______\ \__\ 
    \|_______|\|_______|\|__|\|__|\|_______|\|_______|\|_______|\|_______|\|__| 
                                                                                                                                                                                       
```

A Vert.x-based HTTP/HTTPS forwarding proxy with HMAC-authenticated framing between a local client and a remote server.

## Architecture

`deadbeef-proxy` is a two-process system. The **client** runs on your local machine and exposes a single HTTP proxy port that your browser (or any HTTP/HTTPS client) connects to. The **server** runs on a remote host and forwards traffic onward to the real upstream. The two processes talk over two ports — one for HTTP traffic and one for HTTPS-CONNECT tunnels — using a Protobuf-framed wire format prefixed with the magic `0xDEADBEEF`. Each request is authenticated with an HMAC-SHA256 signature over a per-request nonce and timestamp; secrets never appear on the wire. Both sides use Netty native transports (`epoll` on Linux, `kqueue` on macOS) when available.

## System Requirements

- **JDK 21** (or newer)
- **Maven 3.9+** to build (older Maven versions cannot fully drive the JDK 21 toolchain)

## Building

Compile and package both modules from the project root:

```bash
mvn clean package -DwithNativeDependency=true
```

The shaded fat-jars land in `deadbeef-client/target/` and `deadbeef-server/target/`. The `withNativeDependency=true` profile pulls in the platform-specific Netty native libraries — recommended for best performance.

## Testing

```bash
mvn -B verify
```

## Usage

### Client (local)

Run the client on the machine you want to proxy traffic from. It opens a single port (`localPort`) that any HTTP client can use as a forward proxy.

```bash
java \
  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dio.netty.tryReflectionSetAccessible=true \
  -jar deadbeef-client/target/deadbeef-client-${version}[-${os.detected.classifier}].jar \
  --config client-config.yaml
```

> The `--add-opens` and `-Dio.netty.tryReflectionSetAccessible` flags let Netty use its fast direct-memory paths on JDK 21. They are not strictly required, but skipping them prints warnings and may slightly degrade throughput.

Example `client-config.yaml`:

```yaml
# Required
remoteHost: example.com   # remote server hostname or IP (IPv6 is fine)
httpPort: 14483           # remote server's HTTP port
httpsPort: 14484          # remote server's HTTPS port
localPort: 14482          # local proxy port (point your browser here)
secretId: an-id           # must match a (secretId, secretKey) pair on the server
secretKey: a-key

# Optional
preferNativeTransport: true
addressResolver: [ 8.8.8.8, 114.114.114.114 ]   # custom DNS resolvers; omit to use the system resolver
# httpClient:    {...}    # passthrough to io.vertx.core.http.HttpClientOptions
# netClient:     {...}    # passthrough to io.vertx.core.net.NetClientOptions
# localServer:   {...}    # passthrough to io.vertx.core.http.HttpServerOptions
```

### Server (remote)

Run the server on the remote host. Open the two listening ports (`httpPort`, `httpsPort`) in your firewall.

```bash
nohup java \
  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Dio.netty.tryReflectionSetAccessible=true \
  -jar deadbeef-server/target/deadbeef-server-${version}[-${os.detected.classifier}].jar \
  -c server-config.yaml \
  > run.log 2>&1 &
echo $! > pid.file
```

Example `server-config.yaml`:

```yaml
# Required
httpPort: 14483
httpsPort: 14484
auth:
  # one server can recognize many (secretId, secretKey) pairs;
  # the same secretId may appear multiple times to support key rotation
  - { secretId: one-secret-id,     secretKey: one-secret-key }
  - { secretId: another-secret-id, secretKey: another-secret-key }

# Optional
preferNativeTransport: true
addressResolver: [ 8.8.8.8, 114.114.114.114 ]
# httpClient:  {...}     # HttpClientOptions
# httpServer:  {...}     # HttpServerOptions
# netClient:   {...}     # NetClientOptions
# netServer:   {...}     # NetServerOptions
```

## Configuration reference

The `httpClient`, `httpServer`, `netClient`, `netServer`, and `localServer` blocks deserialize directly into their Vert.x option types via a custom Jackson module (`VertxJsonModule`). Any field accepted by the corresponding Vert.x `*Options` class can be set there — TLS, write queue sizing, connect timeouts, etc.

Auth-window tolerance is 15 minutes on either side of the server's wall clock; keep the two hosts loosely time-synchronized.

## License

See [LICENSE.md](LICENSE.md).
