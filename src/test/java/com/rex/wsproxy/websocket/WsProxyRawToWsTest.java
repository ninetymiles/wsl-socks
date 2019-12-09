package com.rex.wsproxy.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class WsProxyRawToWsTest {

    @Test
    public void testBufferToBinaryFrame() throws Exception {
        EmbeddedChannel inbound = new EmbeddedChannel();
        EmbeddedChannel outbound = new EmbeddedChannel();
        WsProxyRawToWs proxy = new WsProxyRawToWs(outbound);

        inbound.pipeline().addLast(proxy);
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
        WsProxyRawToWs proxy = new WsProxyRawToWs(outbound);

        inbound.pipeline().addLast(proxy);

        StringBuffer sb = new StringBuffer();
        int total = 65536 * 2;
        for (int i = 0; i < total; i++) {
            sb.append((char) ((i % 26) + 'A'));
        }
        inbound.writeInbound(Unpooled.wrappedBuffer(sb.toString().getBytes()));

        int offset = 0;
        BinaryWebSocketFrame frame = outbound.readOutbound();
        while (frame != null) {
            ByteBuffer data = frame.content().nioBuffer();

            int idx = 0; // The first byte
            assertEquals(((idx + offset) % 26) + 'A', data.get(idx));

            idx = data.remaining() - 1; // The last byte
            assertEquals(((idx + offset) % 26) + 'A', data.get(idx));

            idx = data.remaining() / 2; // The middle byte
            assertEquals(((idx + offset) % 26) + 'A', data.get(idx));

            offset += data.remaining();
            frame = outbound.readOutbound();
        }

        inbound.close();
        outbound.close();
    }

    @Test
    public void testRefCount() throws Exception {
        EmbeddedChannel inbound = new EmbeddedChannel();
        EmbeddedChannel outbound = new EmbeddedChannel();
        WsProxyRawToWs proxy = new WsProxyRawToWs(outbound);

        ByteBuf data = Unpooled.wrappedBuffer("HelloWorld!".getBytes());
        assertEquals(1, data.refCnt());

        inbound.pipeline().addLast(proxy);
        inbound.writeInbound(data);

        BinaryWebSocketFrame frame = outbound.readOutbound();
        assertEquals(1, frame.refCnt());
        assertEquals(1, data.refCnt());

        inbound.close();
        outbound.close();
    }
}
