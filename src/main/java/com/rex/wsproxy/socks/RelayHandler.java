package com.rex.wsproxy.socks;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

public final class RelayHandler extends ChannelInboundHandlerAdapter {

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
        //cause.printStackTrace();
        ctx.close();
    }
}
