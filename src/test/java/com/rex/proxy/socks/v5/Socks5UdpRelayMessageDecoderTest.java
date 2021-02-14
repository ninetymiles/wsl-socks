package com.rex.proxy.socks.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class Socks5UdpRelayMessageDecoderTest {

    @Test
    public void testDecode() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(new Socks5UdpRelayMessageDecoder());

        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(100);
        buf.writeByte(0);
        buf.writeByte(Socks5AddressType.IPv4.byteValue());
        Socks5AddressEncoder.DEFAULT.encodeAddress(Socks5AddressType.IPv4, "127.0.0.1", buf);
        buf.writeShort(1024);
        buf.writeBytes(Unpooled.wrappedBuffer("HelloWorld!".getBytes()));
        ch.writeInbound(buf);

        Socks5UdpRelayMessage msg = ch.readInbound();
        assertEquals(100, msg.rsv);
        assertEquals(0, msg.frag);
        assertEquals(Socks5AddressType.IPv4, msg.dstAddrType);
        assertEquals("127.0.0.1", msg.dstAddr);
        assertEquals(1024, msg.dstPort);
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(msg.data.nioBuffer())
                .toString());
    }
}
