package com.rex.socks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class RelayHandlerTest {

    @Test
    public void testBufferToBinary() throws Exception {
        EmbeddedChannel inbound = new EmbeddedChannel();
        EmbeddedChannel outbound = new EmbeddedChannel();
        RelayHandler relay = new RelayHandler(outbound);

        inbound.pipeline().addLast(relay);
        inbound.writeInbound(Unpooled.wrappedBuffer("HelloWorld!".getBytes()));

        ByteBuf frame = outbound.readOutbound();
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(frame.nioBuffer())
                .toString());
    }

    @Test
    public void testLargeBuffer() throws Exception {
        EmbeddedChannel inbound = new EmbeddedChannel();
        EmbeddedChannel outbound = new EmbeddedChannel();
        RelayHandler relay = new RelayHandler(outbound);

        inbound.pipeline().addLast(relay);

        StringBuffer sb = new StringBuffer();
        int total = 65536 * 2;
        for (int i = 0; i < total; i++) {
            sb.append((char) ((i % 26) + 'A'));
        }
        inbound.writeInbound(Unpooled.wrappedBuffer(sb.toString().getBytes()));

        ByteBuf buf = outbound.readOutbound();
        ByteBuffer data = buf.nioBuffer();
        assertEquals(total, buf.readableBytes());

        int idx = 0; // The first byte
        assertEquals(((idx) % 26) + 'A', data.get(idx));

        idx = data.remaining() - 1; // The last byte
        assertEquals(((idx) % 26) + 'A', data.get(idx));

        idx = data.remaining() / 2; // The middle byte
        assertEquals(((idx) % 26) + 'A', data.get(idx));

        Random rand = new Random();
        for (int i = 0; i < 9; i++) {
            idx = rand.nextInt(total);
            assertEquals((idx % 26) + 'A', data.get(idx));
        }

        inbound.close();
        outbound.close();
    }
}
