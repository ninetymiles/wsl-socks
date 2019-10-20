package com.rex.websocket;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

// TODO: Test byte buffer size > 65535, should auto split and join
public class WsProxyRelayWriterTest {

    @Test
    public void testBufferToBinaryFrame() throws Exception {
        EmbeddedChannel inbound = new EmbeddedChannel();
        EmbeddedChannel outbound = new EmbeddedChannel();
        WsProxyRelayWriter writer = new WsProxyRelayWriter(outbound);

        inbound.pipeline().addLast(writer);
        inbound.writeInbound(Unpooled.wrappedBuffer("HelloWorld!".getBytes()));

        BinaryWebSocketFrame frame = outbound.readOutbound();
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(frame.content().nioBuffer())
                .toString());
    }
}
