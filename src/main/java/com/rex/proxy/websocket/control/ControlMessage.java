package com.rex.proxy.websocket.control;

/**
 * WebSocket control message
 *
 * 1. Handshake
 * Server will send hello message when client connected.
 * S -> C {'type':'hello'}
 *
 * If authentication enabled, server will send with auth algorithm and nonce, default hs256 (HmacSHA256)
 * S -> C {'type':'hello', 'action':'hs256', 'token':'NONCE'}
 *
 * 2. Authorization (New Protocol - for connection pooling)
 * After receiving hello with nonce, client can authenticate once per WebSocket connection:
 * C -> S {'type':'authorization', 'token':'TOKEN'}
 *
 * TOKEN = HMAC.init(SECRET).update(NONCE)
 *
 * If token is valid, server responds with:
 * S -> C {'type':'authorized'}
 *
 * After authorization, all subsequent requests on this WebSocket do NOT need tokens.
 * The WebSocket connection remains authenticated even when reused from connection pool.
 *
 * 3. Proxy
 * When handshake completed, client can start request for proxy connection.
 * C -> S {'type':'request', 'action':'connect', 'address':'www.google.com', 'port':'443'}
 *
 * For backward compatibility, old clients can still include token in each request:
 * C -> S {'type':'request', 'action':'connect', 'address':'www.google.com', 'port':'443', 'token':'TOKEN'}
 * TOKEN = HMAC.init(SECRET).update(NONCE).update(address).update(port)
 *
 * If token valid (or connection already authorized) and proxy connection success:
 * S -> C {'type':'response', 'action':'success'}
 *
 * After success response, all the BinaryWebSocketFrame traffics will tunnel between client and proxy host
 *
 * If proxy connection failed, server will send failure response, and force shutdown the socket after 3 seconds timeout,
 * client should close the socket immediately when receive the message.
 * S -> C {'type':'response', 'action':'failure'}
 *
 * If token auth failed, server will send reject response, and force shutdown the socket after 3 seconds
 * S -> C {'type':'response', 'action':'reject'}
 */
public class ControlMessage {
    public String type;
    public String action;

    public String token;
    public String address;
    public Integer port;
}
