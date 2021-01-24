package com.rex.proxy.socks.v5;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Socks5UdpRelayHandler extends SimpleChannelInboundHandler<Socks5UdpRelayMessage> {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks5UdpRelayHandler.class);

    // TCP socket to client app that request for UDP associate
    // UDP socket failure should force close the TCP control channel
    private final Channel mControl;

    public Socks5UdpRelayHandler(Channel channel) {
        mControl = channel;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5UdpRelayMessage msg) throws Exception {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // ctx: [id: 0x0182c0ea, L:/127.0.0.1:1080 - R:/127.0.0.1:54536]
        // cause: java.io.IOException: Connection reset by peer
        sLogger.warn("{} - {}", ctx.channel(), cause.getMessage());
        if (mControl.isActive()) {
            mControl.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
