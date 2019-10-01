package com.rex.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A websocket channel for socks5 connection
 * Receive and parse websocket message, adapt for socks5 protocol
 */
public class ChannelHandlerSocks5Connection extends ChannelInboundHandlerAdapter {

    private static final Logger sLogger = LoggerFactory.getLogger(ChannelHandlerSocks5Connection.class);

    private final Channel mChannel;

    public ChannelHandlerSocks5Connection(Channel channel) {
        mChannel = channel;
        mChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                sLogger.warn("video died {}", future.channel().remoteAddress());
                onClose();
            }
        });
    }

    @Override // ChannelInboundHandlerAdapter
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        if (message instanceof BinaryWebSocketFrame) {
            onHandleBuffer(((BinaryWebSocketFrame) message).content());
        }
    }

    @Override // ChannelInboundHandlerAdapter
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        sLogger.warn("video exception:\n", cause);
    }

    private void onHandleBuffer(ByteBuf buffer) {
        sLogger.trace("");
    }

    private void onClose() {
        sLogger.trace("");
    }
}
