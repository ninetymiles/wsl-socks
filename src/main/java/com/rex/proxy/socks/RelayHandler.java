package com.rex.proxy.socks;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge all data to target channel
 * TODO: Rename as ChannelHandlerBridge
 */
public final class RelayHandler extends ChannelInboundHandlerAdapter {

    private static final Logger sLogger = LoggerFactory.getLogger(RelayHandler.class);

    private final Channel mOutput;

    public RelayHandler(Channel ch) {
        sLogger.trace("<init> ch=<{}>", ch);
        mOutput = ch;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        sLogger.trace("ctx={} msg={}", ctx, msg);
        ReferenceCountUtil.retain(msg);
        if (mOutput.isActive()) {
            mOutput.writeAndFlush(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // ctx: [id: 0x0182c0ea, L:/127.0.0.1:1080 - R:/127.0.0.1:54536]
        // cause: java.io.IOException: Connection reset by peer
        sLogger.warn("{} - {}", ctx.channel(), cause.getMessage());
        if (mOutput.isActive()) {
            mOutput.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
