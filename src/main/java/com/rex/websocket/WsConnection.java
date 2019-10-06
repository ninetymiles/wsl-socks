package com.rex.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * A websocket tunnel connection for byte buffer
 * TODO: Use MessageToMessageCodec to convert BinaryWebSocketFrame to/from ByteBuffer
 */
@ChannelHandler.Sharable
public class WsConnection extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsConnection.class);
    private static final int FRAME_LIMIT = 65535;

    private Channel mChannel;

    public interface Callback {
        void onConnected(WsConnection conn);
        void onReceived(WsConnection conn, ByteBuffer data);
        void onDisconnected(WsConnection conn);
    }
    private Callback mCallback;

    public WsConnection() {
        sLogger.trace("<init>");
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        sLogger.trace("read msg:{}", msg);
        if (msg instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame frame = (BinaryWebSocketFrame) msg;
            if (mCallback != null) {
                mCallback.onReceived(this, frame.content().nioBuffer());
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        sLogger.trace("event:{}", evt);
        if (WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE.equals(evt)) {
            if (mCallback != null) {
                mCallback.onConnected(this);
            }
            mChannel = ctx.channel();
            mChannel.closeFuture().addListener(mCloseListener);
        }
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        sLogger.warn("connection exception\n", cause);
        if (mCallback != null) {
            mCallback.onDisconnected(this);
        }
    }

    public WsConnection setCallback(Callback cb) {
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
            if (mChannel != null) {
                mChannel.writeAndFlush(new BinaryWebSocketFrame(buffer.retainedSlice(start, length)));
            }
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
                mCallback.onDisconnected(WsConnection.this);
            }
        }
    };
}
