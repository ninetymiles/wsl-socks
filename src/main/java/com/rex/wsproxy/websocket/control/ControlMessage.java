package com.rex.wsproxy.websocket.control;

/**
 * WebSocket control message
 * e.g. {'type':'request', 'action':'connect'}
 *      {'type':'response', 'action':'success'}
 *      {'type':'response', 'action':'failure'}
 */
public class ControlMessage {
    public String type;
    public String action;

    public String address;
    public int port;
}
