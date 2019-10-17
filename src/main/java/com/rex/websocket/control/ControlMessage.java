package com.rex.websocket.control;

import java.net.InetAddress;

/**
 * WebSocket control message
 * e.g. {'type':'request', 'action':'connect'}
 *      {'type':'response', 'action':'success'}
 *      {'type':'response', 'action':'failure'}
 */
public class ControlMessage {
    public String type;
    public String action;

    public String domain;
    public InetAddress address;
    public int port;
}
