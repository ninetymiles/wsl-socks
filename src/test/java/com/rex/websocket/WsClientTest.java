package com.rex.websocket;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class WsClientTest {

    @Test
    public void testSend() throws Exception {
        WsClient.Callback cb = mock(WsClient.Callback.class);
        WsClient client = new WsClient()
                .setCallback(cb)
                .start(new URI("ws://echo.websocket.org"));

        verify(cb, timeout(5000)).onConnected(eq(client));
        client.send(ByteBuffer.wrap("HelloWorld!".getBytes()));

        ArgumentCaptor<ByteBuffer> data = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(cb, timeout(1000)).onReceived(eq(client), data.capture());
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(data.getValue())
                .toString());

        client.stop();
    }
}
