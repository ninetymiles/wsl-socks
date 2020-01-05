package com.rex.proxy.socks;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RelayHandler extends ChannelInboundHandlerAdapter {

    private static final Logger sLogger = LoggerFactory.getLogger(RelayHandler.class);

    private final Channel mRelay;

    public RelayHandler(Channel channel) {
        mRelay = channel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ReferenceCountUtil.retain(msg);
        if (mRelay.isActive()) {
            mRelay.writeAndFlush(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // ctx: [id: 0x0182c0ea, L:/127.0.0.1:1080 - R:/127.0.0.1:54536]
        // cause: java.io.IOException: Connection reset by peer
        sLogger.warn("{} - {}", ctx.channel(), cause.getMessage());
        if (mRelay.isActive()) {
            mRelay.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
