package com.rex.websocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receive BinaryWebSocketFrame from websocket channel, write to raw socket channel as ByteBuf
 */
@ChannelHandler.Sharable
public class WsProxyRelayReader extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsProxyRelayReader.class);

    private final Channel mOutput; // Raw socket channel

    public WsProxyRelayReader(Channel channel) {
        sLogger.trace("<init>");
        mOutput = channel;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame msg) throws Exception {
        sLogger.trace("read msg:{}", msg);
        ReferenceCountUtil.retain(msg);
        mOutput.writeAndFlush(msg.content());
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("connection exception\n", cause);
        ctx.close();
    }
}
