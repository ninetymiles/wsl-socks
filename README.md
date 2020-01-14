# WSL Socks Proxy

![](https://github.com/ninetymiles/wsl-socks/workflows/Java%CI/badge.svg)

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

WSL stand for WansonglingTunnel in HangZhou, it is the shortest way escaping from the traffic jam around WestLake to get the free FuxingBridge, it used to be my daily route to the office.

## Usage

### Wsl-Local

A standard socks5 server, forward all the socks data in websocket protocol to wsl-server.

#### Configuration

> java WslLocal -c wsl-local.properties

```
bindAddress=0.0.0.0
bindPort=1080
proxyUri=ws://wsl_server_address:9777
```

If you need auth socks5, add the following properties to enable it.

```
authUser=user
authPassword=password
```

### Wsl-Server

A standard websocket server, tunnel all the binary websocket frames to internet, works as a proxy.

#### Configuration

> java WslServer -c wsl-server.properties

```
bindAddress=0.0.0.0
bindPort=9777
```

#### Basic SSL support

Deploy WslServer with SSL support can secure the connection, just need set property 'ssl' to true. Default will auto generate self-signed certificate for SSL handshake.

```
ssl=true
```

After enable SSL for server, be sure upgrade the schema to 'wss' in local config 'proxyUri', and if use self-signed certificate, set local config 'proxyCertVerify' to false, or else the HTTPS connection will always failed by certificate not trusted.

```
proxyUri=wss://address:9777
proxyCertVerify=false
```

#### Advanced SSL support

If you want to specify the certificate and private key, set with properties 'sslCert' and 'sslKey'.

The certificate file should store in PEM format, and the key file should convert to PKCS8 format. For encrypted private key, set the password by property 'sslKeyPassword'.

```
ssl=true
sslCert=proxy.cert.pem
sslKey=proxy.key.pem
sslKeyPassword=PASSWORD
```

If your server have a domain and a well trusted certificate (e.g. Issued by LetsEncrypt), you can remove 'proxyCertVerify' from local config or set it to 'true'.

#### Authenticate

If you need auth the connection, specify the same 'proxyUid' in both server config and local config. Use [Online Generator](https://www.uuidgenerator.net/) to generate a random one.

```
proxyUid=UUID
```

#### WebSocket handshake path

Default will upgrade all http requests to websocket, if want to filter some of it, specify the path with property 'proxyPath'.

If the path not match, server will send HTTP response '404 Not Found'.

```
proxyPath=/wsl
```

#### Deploy with Nginx

If firewall only allow access port 443, and already running a https service, e.g. nginx, we can config 'location' filter to forward traffics from port 443 to wsl-server port.

```
server {
    listen       443 ssl;
    ...
    location /wsl {
        proxy_redirect        off;
        proxy_pass            http://127.0.0.1:9777;
        proxy_http_version    1.1;
        proxy_set_header      Upgrade        $http_upgrade;
        proxy_set_header      Connection     "upgrade";
        proxy_set_header      Host           $http_host;
    }
}
```

Remember config wsl-server in ws mode by set property 'ssl=false'. Nginx already handle TLS, only forward plain connection to wsl-server.

And if specify 'proxyPath' in wsl-server, nginx must config with the same filter path.

## License

Wsl-Socks is distributed under the terms of the Apache License (Version 2.0). 
