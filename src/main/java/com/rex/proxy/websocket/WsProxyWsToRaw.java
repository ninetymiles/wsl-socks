package com.rex.proxy.websocket;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receive BinaryWebSocketFrame from websocket channel, write to raw socket channel as ByteBuf
 */
@ChannelHandler.Sharable
public class WsProxyWsToRaw extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsProxyWsToRaw.class);

    private final Channel mOutput; // Raw socket channel

    public WsProxyWsToRaw(Channel channel) {
        //sLogger.trace("<init>");
        mOutput = channel;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame msg) throws Exception {
        sLogger.trace("WsToRaw forward msg:{}", msg.content().readableBytes());
        ReferenceCountUtil.retain(msg);
        mOutput.writeAndFlush(msg.content());
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("WsToRaw caught exception\n", cause);
        sLogger.warn("{}", cause.toString());
        if (mOutput.isActive()) {
            mOutput.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
