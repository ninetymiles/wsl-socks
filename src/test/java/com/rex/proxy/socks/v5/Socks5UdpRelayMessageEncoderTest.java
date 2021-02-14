package com.rex.proxy.socks.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.v5.Socks5AddressDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class Socks5UdpRelayMessageEncoderTest {

    @Test
    public void testEncode() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(new Socks5UdpRelayMessageEncoder());
        ch.writeOutbound(new Socks5UdpRelayMessage.Builder()
                .rsv((short) 1)
                .frag((byte) 2)
                .dstAddrType(Socks5AddressType.IPv4)
                .dstAddr("127.0.0.1")
                .dstPort(1024)
                .data(Unpooled.wrappedBuffer("HelloWorld!".getBytes()))
                .build());

        ByteBuf buf = ch.readOutbound();
        assertEquals(1, buf.readShort());
        assertEquals(2, buf.readByte());

        Socks5AddressType type = Socks5AddressType.valueOf(buf.readByte());
        assertEquals(Socks5AddressType.IPv4, type);
        assertEquals("127.0.0.1", Socks5AddressDecoder.DEFAULT.decodeAddress(type, buf));
        assertEquals(1024, buf.readShort());
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(buf.nioBuffer())
                .toString());
    }
}
