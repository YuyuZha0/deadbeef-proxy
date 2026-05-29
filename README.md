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

`deadbeef-proxy` is a two-process system. The **client** runs on your local machine and exposes a single HTTP proxy port that your browser (or any HTTP/HTTPS client) connects to. The **server** runs on a remote host and forwards traffic onward to the real upstream. The two processes talk over a **single port**: HTTP-proxy traffic is sent as a `POST` whose body carries a Protobuf-framed envelope prefixed with the magic `0xDEADBEEF`, while HTTPS tunnels use **standard HTTP CONNECT** upgraded to raw TCP via Vert.x's `HttpServerRequest.toNetSocket(...)`. Each request is authenticated with an HMAC-SHA256 signature over a per-request nonce and timestamp, carried uniformly in the `X-Deadbeef-Auth` header; secrets never appear on the wire. Both sides use Netty native transports (`epoll` on Linux, `kqueue` on macOS) when available.

### Security defenses (server-side)

- **HMAC-SHA256 authentication** with constant-time signature comparison (`MessageDigest.isEqual`).
- **Replay rejection**: every `(secretId, nonce)` pair is single-use within the auth window. Captured tokens cannot be replayed; the Caffeine-backed nonce cache is sized at 2¹⁸ entries with TTL = 2.5× the auth window.
- **SSRF filter** (`org.deadbeef.security.UpstreamAddressFilter`): the server resolves the upstream host via Vert.x's configured `addressResolver:` chain, then rejects loopback, link-local (incl. cloud-metadata `169.254.169.254`), RFC1918, multicast, unspecified, and IPv4 broadcast destinations before opening any TCP connection. Returns `403 Forbidden`. Configurable per category via the filter's builder; v2.0 ships with the strict default.

> **Migration notes**:
>
> - **v1.x → v2.0** is wire-incompatible. The HTTPS tunnel protocol switched from a bespoke `NetServer`-on-`httpsPort` framing to standard HTTP CONNECT on the same port as the HTTP-proxy flow; the auth header was renamed `X-Deadbeaf-Auth` → `X-Deadbeef-Auth`. Roll client and server together.

## System Requirements

- **JDK 21** (or newer)
- **Maven 3.6.3+** to build (any reasonably recent Maven works; the project has been verified on 3.6.3 with JDK 21)

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
remotePort: 14483         # remote server's listening port (carries both HTTP-proxy and CONNECT)
localPort: 14482          # local proxy port (point your browser here)
secretId: an-id           # must match a (secretId, secretKey) pair on the server
secretKey: a-key

# Optional
preferNativeTransport: true
addressResolver: [ 8.8.8.8, 114.114.114.114 ]   # custom DNS resolvers; omit to use the system resolver
adminPort: 18080          # opens the live metrics dashboard on http://127.0.0.1:18080 (omit to disable)
# httpClient:    {...}    # passthrough to io.vertx.core.http.HttpClientOptions (used for both HTTP-proxy and CONNECT)
# localServer:   {...}    # passthrough to io.vertx.core.http.HttpServerOptions (the browser-facing port)
```

### Server (remote)

Run the server on the remote host. Open the single listening port (`port`) in your firewall.

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
port: 14483
auth:
  # one server can recognize many (secretId, secretKey) pairs;
  # the same secretId may appear multiple times to support key rotation
  - { secretId: one-secret-id,     secretKey: one-secret-key }
  - { secretId: another-secret-id, secretKey: another-secret-key }

# Optional
preferNativeTransport: true
addressResolver: [ 8.8.8.8, 114.114.114.114 ]
# httpClient:  {...}     # HttpClientOptions (server's outbound HTTP-proxy client)
# httpServer:  {...}     # HttpServerOptions (the proxy listening socket)
# netClient:   {...}     # NetClientOptions  (server's outbound TCP client for CONNECT tunnels)
```

### Live metrics dashboard (client)

When `adminPort` is set, the client opens a tiny HTTP server bound to `127.0.0.1:<adminPort>` (loopback only — never reachable from the LAN) that serves a single-page metrics dashboard. Open `http://127.0.0.1:<adminPort>/` in a browser; the page polls `/api/metrics` every 2 seconds and renders ECharts-based throughput / latency / status-code visualisations.

Dashboard dependencies are loaded from public CDNs (`cdn.jsdelivr.net` for ECharts and Pico CSS) — no npm, no build pipeline, no bundled assets beyond a single `dashboard.html` on the classpath. Data lives in memory only; closing the page resets the rolling charts. Omit `adminPort` to disable the endpoint entirely.

Tracked metrics (all under the `proxy.*` namespace):

- **HTTP-proxy flow** — `requests.total`, `requests.failed`, `requests.in_flight`, `responses.{2xx,3xx,4xx,5xx}`, `request.duration` (timer), `bytes.up`, `bytes.down`.
- **HTTPS-tunnel flow** — `tunnels.opened`, `tunnels.failed`, `tunnels.active`, `connect.duration` (timer), `bytes.up`, `bytes.down`.

## Configuration reference

The `httpClient`, `httpServer`, `netClient`, and `localServer` blocks deserialize directly into their Vert.x option types via a custom Jackson module (`VertxJsonModule`). Any field accepted by the corresponding Vert.x `*Options` class can be set there — TLS, write queue sizing, connect timeouts, etc.

Auth-window tolerance is **10 minutes** on either side of the server's wall clock; keep the two hosts loosely time-synchronized.

## License

See [LICENSE.md](LICENSE.md).
