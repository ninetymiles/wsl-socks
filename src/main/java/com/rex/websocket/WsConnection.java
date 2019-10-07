package com.rex.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
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
    private final Callback mCallback;

    public WsConnection(Callback cb) {
        sLogger.trace("<init>");
        mCallback = cb;
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

    @Override // SimpleChannelInboundHandler
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        sLogger.trace("event:{}", evt);
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) { // Replace deprecated WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE
            WebSocketServerProtocolHandler.HandshakeComplete event = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            mChannel = ctx.channel();
            sLogger.info("server connection {} - {} subprotocol {}", mChannel.localAddress(), mChannel.remoteAddress(), event.selectedSubprotocol());
        }
        if (WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE.equals(evt)) {
            mChannel = ctx.channel();
            sLogger.info("client connection {} - {}", mChannel.localAddress(), mChannel.remoteAddress());
        }
        if (mChannel != null) {
            mChannel.closeFuture().addListener(mCloseListener);
            if (mCallback != null) {
                mCallback.onConnected(this);
            }
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

//        ByteBuf buffer = Unpooled.copiedBuffer(data);
//        WebSocketFrame frame = new BinaryWebSocketFrame(buffer);
//        mChannelFuture.channel().writeAndFlush(frame);
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
            sLogger.warn("connection lost {}", mChannel.remoteAddress());
            if (mCallback != null) {
                mCallback.onDisconnected(WsConnection.this);
            }
        }
    };
}
