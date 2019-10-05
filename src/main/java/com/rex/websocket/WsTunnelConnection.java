package com.rex.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * A websocket channel for socks5 connection
 * Receive and parse websocket message, adapt for socks5 protocol
 * TODO: Use MessageToMessageCodec to convert BinaryWebSocketFrame to/from ByteBuffer
 */
public class WsTunnelConnection extends ChannelInboundHandlerAdapter {

    private static final Logger sLogger = LoggerFactory.getLogger(WsTunnelConnection.class);
    private static final int FRAME_LIMIT = 65535;

    private final Channel mChannel;

    public interface Callback {
        void onReceived(WsTunnelConnection conn, ByteBuffer data);
        void onClosed(WsTunnelConnection conn);
    }
    private Callback mCallback;

    public WsTunnelConnection(Channel outbound) {
        sLogger.trace("");
        mChannel = outbound;
        mChannel.closeFuture().addListener(mCloseListener);
    }

    @Override // ChannelInboundHandlerAdapter
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        if (message instanceof BinaryWebSocketFrame) {
            if (mCallback != null) {
                mCallback.onReceived(this, ((BinaryWebSocketFrame) message).content().nioBuffer());
            }
        }
    }

    @Override // ChannelInboundHandlerAdapter
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        sLogger.warn("video exception:\n", cause);
    }

    public WsTunnelConnection setCallback(Callback cb) {
        mCallback = cb;
        return this;
    }

    public void send(ByteBuffer data) {
        sLogger.trace("data:{}", data.remaining());

        ByteBuf buffer = Unpooled.copiedBuffer(data);
        int start = 0;
        do {
            int length = Math.min(FRAME_LIMIT, buffer.readableBytes() - start);
            sLogger.trace("send {}-{}/{}", start, (start + length - 1), buffer.readableBytes());
            mChannel.writeAndFlush(new BinaryWebSocketFrame(buffer.retainedSlice(start, length)));
            start += length;
        } while (start < buffer.readableBytes());
    }

    public void close() {
        sLogger.trace("");
        if (mChannel != null) {
            //mChannel.closeFuture().removeListener(mCloseListener);
            mChannel.close();
        }
    }

    private ChannelFutureListener mCloseListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            sLogger.warn("connection died {}", future.channel().remoteAddress());
            if (mCallback != null) {
                mCallback.onClosed(WsTunnelConnection.this);
            }
        }
    };
}
