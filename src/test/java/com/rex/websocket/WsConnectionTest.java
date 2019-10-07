package com.rex.websocket;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

// TODO: Test bytebuffer > 65535, should auto split and join
public class WsConnectionTest {

    @Test
    public void testSend() throws Exception {
        EmbeddedChannel channel = spy(new EmbeddedChannel());
        WsConnection.Callback cb = mock(WsConnection.Callback.class);
        WsConnection conn = new WsConnection()
                .setCallback(cb);

        // Simulate the connection handshake completed
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        conn.userEventTriggered(ctx, WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE);
        verify(cb, timeout(1000)).onConnected(eq(conn));

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
        WsConnection.Callback cb = mock(WsConnection.Callback.class);
        WsConnection conn = new WsConnection()
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
        WsConnection.Callback cb = mock(WsConnection.Callback.class);
        WsConnection conn = new WsConnection()
                .setCallback(cb);

        // Simulate the connection handshake completed
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        conn.userEventTriggered(ctx, WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE);
        verify(cb, timeout(1000)).onConnected(eq(conn));

        // Close the connection should invoke onClosed()
        channel.close();
        verify(cb, timeout(1000)).onDisconnected(eq(conn));


        // Close the channel should also invoke onClosed()
        reset(cb);
        channel = new EmbeddedChannel();
        conn = new WsConnection().setCallback(cb);

        when(ctx.channel()).thenReturn(channel);
        conn.userEventTriggered(ctx, WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE);

        conn.close();
        verify(cb, timeout(1000)).onDisconnected(eq(conn));
    }
}
