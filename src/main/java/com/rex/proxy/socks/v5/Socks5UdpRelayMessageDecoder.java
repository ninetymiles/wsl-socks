package com.rex.proxy.socks.v5;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;

import java.util.List;

public class Socks5UdpRelayMessageDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        final short rsv = in.readShort();
        final byte frag = in.readByte();
        final Socks5AddressType dstAddrType = Socks5AddressType.valueOf(in.readByte());
        final String dstAddr = Socks5AddressDecoder.DEFAULT.decodeAddress(dstAddrType, in);
        final int dstPort = in.readUnsignedShort();
        out.add(new Socks5UdpRelayMessage.Builder()
                .rsv(rsv)
                .frag(frag)
                .dstAddrType(dstAddrType)
                .dstAddr(dstAddr)
                .dstPort(dstPort)
                .data(in)
                .build());
        ctx.pipeline().remove(this);
    }
}
