package com.rex.websocket;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class WsServerTest {

    @Test
    public void testAcceptAndClose() throws Exception {
        WsServer.Callback cb = mock(WsServer.Callback.class);
        WsServer server = new WsServer()
                .setCallback(cb)
                .start(new InetSocketAddress(9777));

        // Stop client, should trigger server callback onRemoved()
        URI uri = new URI("ws://localhost:9777/ws");
        WsClient client = new WsClient()
                .setSubProtocol(WsServerPathInterceptor.SUBPROTOCOL)
                .start(uri);

        ArgumentCaptor<WsConnection> conn = ArgumentCaptor.forClass(WsConnection.class);
        verify(cb, timeout(5000)).onAdded(eq(server), conn.capture());

        client.stop();
        verify(cb, timeout(5000)).onRemoved(eq(server), eq(conn.getValue()));


        // Stop server, should also trigger callback onRemoved()
        reset(cb);
        client.start(uri);
        verify(cb, timeout(5000)).onAdded(eq(server), conn.capture());

        server.stop();
        verify(cb, timeout(5000)).onRemoved(eq(server), eq(conn.getValue()));
    }

    @Test
    public void testSend() throws Exception {
        WsServer.Callback cb = mock(WsServer.Callback.class);
        WsServer server = new WsServer()
                .setCallback(cb)
                .start(new InetSocketAddress(9777));

        WsClient client = new WsClient()
                .setSubProtocol(WsServerPathInterceptor.SUBPROTOCOL)
                .start(new URI("ws://localhost:9777/ws"));

        ArgumentCaptor<WsConnection> conn = ArgumentCaptor.forClass(WsConnection.class);
        verify(cb, timeout(5000)).onAdded(eq(server), conn.capture());

        client.send(ByteBuffer.wrap("HelloWorld!".getBytes()));
        ArgumentCaptor<ByteBuffer> data = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(cb, timeout(1000)).onReceived(eq(server), eq(conn.getValue()), data.capture());
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(data.getValue())
                .toString());

        server.stop();
    }
}
