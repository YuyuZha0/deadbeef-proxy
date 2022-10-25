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
```
