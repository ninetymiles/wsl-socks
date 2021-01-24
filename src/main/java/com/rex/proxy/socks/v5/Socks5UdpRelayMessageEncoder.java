package com.rex.proxy.socks.v5;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder;

public class Socks5UdpRelayMessageEncoder extends MessageToByteEncoder<Socks5UdpRelayMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks5UdpRelayMessage msg, ByteBuf out) throws Exception {
        out.writeShort(msg.rsv);
        out.writeByte(msg.frag);
        out.writeByte(msg.dstAddrType.byteValue());
        Socks5AddressEncoder.DEFAULT.encodeAddress(msg.dstAddrType, msg.dstAddr, out);
        out.writeShort(msg.dstPort);
    }
}
