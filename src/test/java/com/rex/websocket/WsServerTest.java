package com.rex.websocket;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class WsServerTest {

    @Test
    public void testConfigPort() throws Exception {
        final int port = 1234;
        WsServer server = new WsServer()
                .config(new WsServer.Configuration("127.0.0.1", port))
                .start();

        String url = "http://127.0.0.1:" + port + "/";
        URLConnection conn = new URI(url)
                .toURL()
                .openConnection();
        HttpURLConnection connHttp = (HttpURLConnection) conn;
        assertEquals(404, connHttp.getResponseCode());
        assertEquals("Not Found", connHttp.getResponseMessage());

        server.stop();
    }

    @Test
    public void testConfigFile() throws Exception {
        // getClass().getResourceAsStream() point to build/classes/java/test/
        // ClassLoader.getSystemResourceAsStream() point to build/resources/
        WsServer server = new WsServer()
                .config(ClassLoader.getSystemResourceAsStream("config_127.0.0.1_4321.properties"))
                .start();

        String url = "http://127.0.0.1:4321/";
        URLConnection conn = new URI(url)
                .toURL()
                .openConnection();
        HttpURLConnection connHttp = (HttpURLConnection) conn;
        assertEquals(404, connHttp.getResponseCode());

        server.stop();
    }

    @Test
    public void testAcceptAndClose() throws Exception {
        WsServer.Callback cb = mock(WsServer.Callback.class);
        WsServer server = new WsServer()
                .setCallback(cb)
                .start();

        // Stop client, should trigger server callback onRemoved()
        URI uri = new URI("ws://localhost:" + server.port() + "/ws");
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
                .start();

        WsClient client = new WsClient()
                .setSubProtocol(WsServerPathInterceptor.SUBPROTOCOL)
                .start(new URI("ws://localhost:"  + server.port() + "/ws"));

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
