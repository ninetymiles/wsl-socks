# WebSocketProxy

![](https://github.com/zjuliuj05/wsproxy/workflows/Java%CI/badge.svg)

A websocket tunnel proxy, upgrade protocol from HTTP or HTTPS, help get through firewall.

## Table of Contents

- [Background](#background)
- [Install](#install)
- [Usage](#usage)
	- [WsLocal](#wslocal)
	- [WsServer](#wsserver)
- [License](#license)

## Background

In some network environment, the network traffics was restricted, only allows HTTP and HTTPS traffics.

WebSocket is a HTTP based transmission protocol, widely used by HTML5 applications, easy to transfer text message and binary messages, support deploy with HTTPS protocol to enhance the security. Suitable for implement tunnel proxy to access internet.

## Install

First install java

- MacOS

```
$ brew cask install java
```

- Ubuntu

``` 
$ sudo apt-get install default-jre
```

Second uncompress the tarball directly

## Usage

### WsLocal

A standard socks5 server, relay all the socks data in websocket protocol to WsServer.

#### Configuration

> java WsProxyLocal -c wslocal.properties

```
bindAddress=0.0.0.0
bindPort=1080
proxyUri=ws://my_wsproxy_address:9777
```

If you need auth socks5, add the following lines in wslocal.properties to enable it.

```
authUser=user
authPassword=password
```

### WsServer

A standard websocket server, tunnel all the binary websocket frames to internet, works as a proxy server.

#### Configuration

> java WsProxyServer -c wsserver.properties

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

Update the wsserver.properties

```
sslCert=wsproxy.cert.pem
sslKey=wsproxy.key.pem
sslKeyPassword=
```

Update the wslocal.properties, use 'wss' instead of 'ws' for 'proxyUri', and set 'proxyCertVerify' false to disable self-signed certificate verify, or else the HTTPS connection may always failed.

```
proxyUri=wss://my_wsproxy_address:9777
proxyCertVerify=false
```

If your server have a valid domain, and also have a valid certificated, e.g. issued by 'LetsEncrypt' or other CA, then you can remove the 'proxyCertVerify' config or set it to 'true'.

If you need auth the connection, specify 'proxyUid' for both wsserver and wslocal property file

```
proxyUid=UUID
```

## License

WsProxy is distributed under the terms of the Apache License (Version 2.0). 
