# WsProxy Changelog

This file is used to list changes made in each version of the WsProxy project.

## v1.3 (20200115)

- Upgrade subprotocol to com.rex.websocket.protocol.proxy2 for supporting websocket auth
- Remove TCP_NODELAY, optimize for bandwidth
- Support enable ssl with auto generated self-sign certificate
- Support specify url path when upgrade websocket protocol, default will accept all path

## v1.2 (20191204)

- Fix memory leaks in RelayWriter
- Support ignore certificate
- Remove socks4 support, even java8 internal socks4 implementation broken, socks4 almost useless
- Use logback instead of sl4j-simple

## v1.1 (20191115)

- Fix use 65536 as max frame limit, large than websocket handshake limit 65535 and failed connection

## v1.0 (20191112)

- Support basic websocket tunnel proxy
- Support basic socks4/5 websocket tunnel client

