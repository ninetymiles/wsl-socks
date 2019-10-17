package com.rex.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receive ByteBuf from raw socket channel, write to websocket as BinaryWebSocketFrame
 */
@ChannelHandler.Sharable
public class WsProxyRelayWriter extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsProxyRelayWriter.class);

    private final Channel mOutput; // WebSocket channel

    public WsProxyRelayWriter(Channel outbound) {
        sLogger.trace("<init>");
        mOutput = outbound;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        sLogger.trace("read msg:{}", msg);
        if (msg instanceof ReferenceCounted) {
            ((ReferenceCounted) msg).retain();
        }
        mOutput.writeAndFlush(new BinaryWebSocketFrame(msg));
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        sLogger.warn("connection exception\n", cause);
    }
}
