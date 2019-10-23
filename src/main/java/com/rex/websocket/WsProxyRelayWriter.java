package com.rex.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receive ByteBuf from raw socket channel, write to websocket as BinaryWebSocketFrame
 */
@ChannelHandler.Sharable
public class WsProxyRelayWriter extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsProxyRelayWriter.class);
    private static final int FRAME_LIMIT = 1 << 16; // 65536

    private final Channel mOutput; // WebSocket channel

    public WsProxyRelayWriter(Channel outbound) {
        sLogger.trace("<init>");
        mOutput = outbound;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf data) throws Exception {
        sLogger.trace("read data:{}", data.readableBytes());
        ReferenceCountUtil.retain(data);

        int start = 0;
        do {
            int length = Math.min(FRAME_LIMIT, data.readableBytes() - start);
            sLogger.trace("send {}-{}/{}", start, (start + length - 1), data.readableBytes());
            mOutput.writeAndFlush(new BinaryWebSocketFrame(data.retainedSlice(start, length)));
            start += length;
        } while (start < data.readableBytes());
        //mOutput.writeAndFlush(new BinaryWebSocketFrame(data));
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("connection exception\n", cause);
        ctx.close();
    }
}
