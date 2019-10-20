package com.rex.websocket;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void testLargeBufferToBinaryFrame() throws Exception {
        EmbeddedChannel inbound = new EmbeddedChannel();
        EmbeddedChannel outbound = new EmbeddedChannel();
        WsProxyRelayWriter reader = new WsProxyRelayWriter(outbound);

        inbound.pipeline().addLast(reader);

        StringBuffer sb = new StringBuffer();
        int total = 65536 * 2;
        for (int i = 0; i < total; i++) {
            sb.append((char) ((i % 26) + 'A'));
        }
        inbound.writeInbound(Unpooled.wrappedBuffer(sb.toString().getBytes()));

        BinaryWebSocketFrame frame = outbound.readOutbound();
        ByteBuffer data = frame.content().nioBuffer();

        int idx = 0; // The first byte
        assertEquals((idx % 26) + 'A', data.get(idx));

        idx = sb.length() - 1; // The last byte
        assertEquals((idx % 26) + 'A', data.get(idx));

        Random rand = new Random();
        for (int i = 0; i < 9; i++) {
            idx = rand.nextInt(total);
            assertEquals((idx % 26) + 'A', data.get(idx));
        }

        inbound.close();
        outbound.close();
    }
}
