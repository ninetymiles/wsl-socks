package com.rex.wsproxy.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class WsProxyRelayReaderTest {

    @Test
    public void testBinaryFrameToBuffer() throws Exception {
        EmbeddedChannel inbound = new EmbeddedChannel();
        EmbeddedChannel outbound = new EmbeddedChannel();
        WsProxyRelayReader reader = new WsProxyRelayReader(outbound);

        inbound.pipeline().addLast(reader);
        inbound.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer("HelloWorld!".getBytes())));

        ByteBuf data = outbound.readOutbound();
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(data.nioBuffer())
                .toString());

        inbound.close();
        outbound.close();
    }

    @Test
    public void testLargeBinaryFrameToBuffer() throws Exception {
        EmbeddedChannel inbound = new EmbeddedChannel();
        EmbeddedChannel outbound = new EmbeddedChannel();
        WsProxyRelayReader reader = new WsProxyRelayReader(outbound);

        inbound.pipeline().addLast(reader);

        StringBuffer sb = new StringBuffer();
        int total = 65536 * 2;
        for (int i = 0; i < total; i++) {
            sb.append((char) ((i % 26) + 'A'));
        }
        inbound.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(sb.toString().getBytes())));

        ByteBuf data = outbound.readOutbound();

        int idx = 0; // The first byte
        assertEquals((idx % 26) + 'A', data.nioBuffer().get(idx));

        idx = sb.length() - 1; // The last byte
        assertEquals((idx % 26) + 'A', data.nioBuffer().get(idx));

        Random rand = new Random();
        for (int i = 0; i < 9; i++) {
            idx = rand.nextInt(total);
            assertEquals((idx % 26) + 'A', data.nioBuffer().get(idx));
        }

        inbound.close();
        outbound.close();
    }

    @Test
    public void testRefCount() throws Exception {
        EmbeddedChannel inbound = new EmbeddedChannel();
        EmbeddedChannel outbound = new EmbeddedChannel();
        WsProxyRelayReader reader = new WsProxyRelayReader(outbound);

        WebSocketFrame frame = new BinaryWebSocketFrame(Unpooled.wrappedBuffer("HelloWorld!".getBytes()));
        assertEquals(1, frame.refCnt());
        inbound.pipeline().addLast(reader);
        inbound.writeInbound(frame);

        ByteBuf data = outbound.readOutbound();
        assertEquals(1, data.refCnt());
        assertEquals(1, frame.refCnt());

        inbound.close();
        outbound.close();
    }
}
