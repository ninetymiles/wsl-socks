package com.rex.websocket;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

// TODO: Test bytebuffer > 65535, should auto split and join
public class WsTunnelConnectionTest {

    @Test
    public void testSend() throws Exception {
        EmbeddedChannel channel = spy(new EmbeddedChannel());
        WsTunnelConnection.Callback cb = mock(WsTunnelConnection.Callback.class);
        WsTunnelConnection conn = new WsTunnelConnection(channel)
                .setCallback(cb);

        conn.send(ByteBuffer.wrap("HelloWorld!".getBytes(StandardCharsets.UTF_8)));

        // Connection should wrap the data in websocket frame
        ArgumentCaptor<BinaryWebSocketFrame> frame = ArgumentCaptor.forClass(BinaryWebSocketFrame.class);
        verify(channel, timeout(1000)).writeAndFlush(frame.capture());
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(frame.getValue().content().nioBuffer())
                .toString());

        conn.close();
    }

    @Test
    public void testReceive() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        WsTunnelConnection.Callback cb = mock(WsTunnelConnection.Callback.class);
        WsTunnelConnection conn = new WsTunnelConnection(channel)
                .setCallback(cb);

        channel.pipeline().addLast(conn);
        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer("HelloWorld!".getBytes())));

        // Connection should callback received data
        ArgumentCaptor<ByteBuffer> data = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(cb, timeout(1000)).onReceived(eq(conn), data.capture());
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(data.getValue())
                .toString());

        conn.close();
    }

    @Test
    public void testClose() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        WsTunnelConnection.Callback cb = mock(WsTunnelConnection.Callback.class);
        WsTunnelConnection conn = new WsTunnelConnection(channel)
                .setCallback(cb);

        // Close the connection should invoke onClosed()
        channel.close();
        verify(cb, timeout(1000)).onClosed(eq(conn));


        // Close the channel should also invoke onClosed()
        reset(cb);
        conn = new WsTunnelConnection(new EmbeddedChannel()).setCallback(cb);
        conn.close();
        verify(cb, timeout(1000)).onClosed(eq(conn));
    }
}
