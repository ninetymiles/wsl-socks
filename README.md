# WebSocketProxy

A websocket proxy, deploy with TLS to secure the connection.

## LocalClient

A standard socks4/5 server, relay all the socks data in websocket protocol to remote secured websocket server.

## RemoteServer

A standard websocket server, tunnel all the binary websocket frames as proxy

Deploy with TLS encryption, protect the proxy data and protocol, avoid proxy stream be blocked by firewall.

## Certificate

Generate a new key and self-signed certificate, with password 'TestOnly'

```
keytool -genkey -v -keystore ssl.keystore -alias wsproxy -keyalg RSA -keysize 2048 -validity 36500
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

