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

`java(jdk/jre) >= 11`

**If you want to compile the project yourself, you may need `maven`(version of `3.6.3` is preferred)**

The project contains some platform dependent components, it is recommended for you to compile the code on you device to
gain better performance.

### Usage

#### Local

Start a deamon process listening on a port with the following command:

```bash
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -Dio.netty.tryReflectionSetAccessible=true -jar deadbeef-client/target/deadbeef-client-1.0-SNAPSHOT.jar --config client-config.yaml
```
*No doubt that you could custom as many JVM options as you like*

Here is the `client-config.yaml` example:

```yaml
httpPort: 14483 # the http port of remote server
httpsPort: 14484 # the https port of remote server
remoteServer: example.com # the remote server address, ipv6 address is supported if your machine has access to ipv6 network
localPort: 14482 # this is the local port that you could configure your browser proxy to
secretId: a-secret-id
secretKey: a-secret-key
# options below are optional
preferNativeTransport: true
addressResolver: [ 8.8.8.8, 114.114.114.114 ] # custom dns hosts, you may just keep it empty
```

#### Remote

Start a deamon process on your remote machine to handle requests from you local proxy server.

**Note: `deadbeef-server` handles HTTP/HTTPS on different ports, configure your firewall properly**

You may start with the following command:

```bash
#!/bin/bash
nohup java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -Dio.netty.tryReflectionSetAccessible=true -jar deadbeef-server/target/deadbeef-server-1.0-SNAPSHOT.jar -c server-config.yaml > run.log 2>&1 & echo $! > pid.file
```

Here is what `server-config.yaml` should be like:

```yaml
httpPort: 14483
httpsPort: 14484
auth: # authentication key pairs, one secretId might have multiple secretKeys
  - { secretKey: one-secret-key, secretId: one-secret-id }
  - { secretKey: another-secret-key, secretId: another-secret-id }
preferNativeTransport: true
addressResolver: [ 8.8.8.8, 114.114.114.114 ]
```
