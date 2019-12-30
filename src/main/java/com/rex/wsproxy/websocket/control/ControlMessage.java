package com.rex.wsproxy.websocket.control;

/**
 * WebSocket control message
 *
 * 1. Handshake
 * If authentication enabled, server will send challenge message first when client connected.
 * client must use secret key (e.g. a UUID string) to generate hash token for ack immediately.
 * TOKEN = sha256(NONCE + SECRET)
 * e.g.
 * S -> C {'type':'challenge', 'action':'NONCE'}
 * C -> S {'type':'ack', 'action':'TOKEN'}
 *
 * When auth success or do not require for auth, server will send ready message and start accepting proxy request
 * e.g.
 * S -> C {'type':'status', 'action':'ready'}
 *
 * If auth failed, server will send reject message, and force shutdown the socket after 3 seconds timeout
 * client should close socket immediately when receive the message to avoid server TIME-WAIT
 * e.g.
 * S -> C {'type':'status', 'action':'reject'}
 *
 * 2. Proxy
 * When server ready, client can start request for proxy connection.
 * e.g.
 * C -> S {'type':'request', 'action':'connect', 'address':'www.google.com', 'port':'443'}
 * S -> C {'type':'response', 'action':'success'}
 *
 * After success response, all the BinaryWebSocketFrame traffics will tunnel between client and proxy host
 *
 * If proxy connection failed, or server not ready, server will send failure response, and force shutdown the socket after 3 seconds timeout,
 * client should close the socket immediately when receive the message.
 * e.g.
 * S -> C {'type':'response', 'action':'failure'}
 */
public class ControlMessage {
    public String type;
    public String action;

    public String address;
    public int port;
}
