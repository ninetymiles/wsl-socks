# WebSocketSocksProxy

A websocket tunneled socks5 proxy, deploy with TLS to secure the connection.

## Client

Secured Websocket client, handshake with standard https protocol.
Also works as standard socks5 server, tunnel all the socks5 data stream in websocket protocol.

## Server

A standard websocket server, convert all the binary websocket frame back to socks protocol
Backed with a standard socks5 proxy or embed a socks5 proxy directly.

Deploy with TLS encryption, to hide the socks5 protocol, avoid the stream be blocked by firewall.
