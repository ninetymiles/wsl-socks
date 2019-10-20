package com.rex.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

// TODO: Test byte buffer size > 65535, should auto split and join
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
}
