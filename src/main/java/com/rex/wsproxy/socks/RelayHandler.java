package com.rex.wsproxy.socks;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
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
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        //sLogger.debug("Relay registered - {}", mRelay);
        ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                sLogger.debug("Relay peer closed {}", future.channel());
                sLogger.debug("Relay force close {}", mRelay);
                if (mRelay.isActive()) {
                    mRelay.writeAndFlush(Unpooled.EMPTY_BUFFER)
                            .addListener(ChannelFutureListener.CLOSE);
                }
            }
        });
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
        sLogger.warn("RelayHandler caught exception\n", cause);
        //cause.printStackTrace();
        //ctx.close();
        //mRelay.close();
        if (mRelay.isActive()) {
            mRelay.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
