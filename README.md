# WebSocketTunnelProxy

A websocket tunneled socks5 proxy, deploy with TLS to secure the connection.

## Client

Secured Websocket client, handshake with standard https protocol.
Also works as standard socks5 server, tunnel all the socks5 data stream in websocket protocol.

## Server

A standard websocket server, convert all the binary websocket frame back to socks protocol
Backed with a standard socks5 proxy or embed a socks5 proxy directly.

Deploy with TLS encryption, to hide the socks5 protocol, avoid the stream be blocked by firewall.

## Certificate

Generate a new key and self-signed certificate, with password 'TestOnly'

```
keytool -genkey -v -keystore ssl.keystore -alias wstp -keyalg RSA -keysize 2048 -validity 36500
keytool -importkeystore -srckeystore ssl.keystore -destkeystore ssl.keystore -deststoretype pkcs12
keytool -list -v -keystore ssl.keystore
```

Export the key and cert as pem file, in pkcs12 format

```
openssl pkcs12 -in test.keystore -nocerts -out test.key.p12.pem
openssl pkcs12 -in test.keystore -nokeys -clcerts -out test.cert.pem
```

Convert the key file to PKCS8 format, java provide PKCS8EncodedKeySpec to load unencrypted key

``
openssl pkcs8 -in test.key.p12.pem -nocrypt -topk8 -out test.key.p8.pem
``

