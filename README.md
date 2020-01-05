# WSL Socks Proxy

![](https://github.com/zjuliuj05/wsl-socks/workflows/Java%CI/badge.svg)

A socks proxy, tunnel traffics by secured websocket (WebSocket over TLS), help bypass firewall.

## Table of Contents

- [Background](#background)
- [Usage](#usage)
	- [Local](#wsl-local)
	- [Server](#wsl-server)
- [License](#license)

## Background

In some network environment, the network traffics was restricted, only allows HTTP and HTTPS traffics.

WebSocket is a HTTP based transmission protocol, widely used by HTML5 applications, easy to transfer text message and binary messages, support deploy with HTTPS protocol to enhance the security. Suitable for implement tunnel proxy to access internet.

WSL stand for WansonglingTunnel, it is the sortest way help you escape from the traffic jam around WestLake to get the free FuxingBridge.

## Usage

### Wsl-Local

A standard socks5 server, forward all the socks data in websocket protocol to wsl-server.

#### Configuration

> java WslLocal -c wsl-local.properties

```
bindAddress=0.0.0.0
bindPort=1080
proxyUri=ws://address:9777
```

If you need auth socks5, add the following lines in wsl-local.properties to enable it.

```
authUser=user
authPassword=password
```

### Wsl-Server

A standard websocket server, tunnel all the binary websocket frames to internet, works as a proxy server.

#### Configuration

> java WslServer -c wsl-server.properties

```
bindAddress=0.0.0.0
bindPort=9777
```

#### Generate certificate (Optional)

Generate certificate can deploy WsServer with HTTPS protocol to secure the connection.

Generate a new key and self-signed certificate

```
keytool -genkey -v -keystore wsproxy.keystore -alias wsproxy -keyalg RSA -keysize 2048 -validity 36500
keytool -importkeystore -srckeystore wsproxy.keystore -destkeystore wsproxy.keystore -deststoretype pkcs12
keytool -list -v -keystore wsproxy.keystore
```

Export the key and cert as pem file, in pkcs12 format

```
openssl pkcs12 -in wsproxy.keystore -nocerts -out wsproxy.key.p12.pem
openssl pkcs12 -in wsproxy.keystore -nokeys -clcerts -out wsproxy.cert.pem
```

Convert the key file to PKCS8 format, java provide PKCS8EncodedKeySpec to load unencrypted key

```
openssl pkcs8 -in wsproxy.key.p12.pem -nocrypt -topk8 -out wsproxy.key.p8.pem
```

Update the wsl-server.properties

```
sslCert=wsproxy.cert.pem
sslKey=wsproxy.key.pem
sslKeyPassword=
```

Update the wsl-local.properties, use 'wss' instead of 'ws' for 'proxyUri', and set 'proxyCertVerify' false to disable self-signed certificate verify, or else the HTTPS connection may always failed.

```
proxyUri=wss://address:9777
proxyCertVerify=false
```

If your server have a valid domain, and also have a valid certificated, e.g. issued by 'LetsEncrypt' or other CA, then you can remove the 'proxyCertVerify' config or set it to 'true'.

If you need auth the connection, specify 'proxyUid' for both wsl-server and wsl-local property file

```
proxyUid=UUID
```

## License

Wsl-Socks is distributed under the terms of the Apache License (Version 2.0). 
