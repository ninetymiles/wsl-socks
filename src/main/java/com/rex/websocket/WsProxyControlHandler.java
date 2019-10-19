package com.rex.websocket;

import com.rex.websocket.control.ControlMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * A websocket proxy connection
 * TODO: Use MessageToMessageCodec to convert BinaryWebSocketFrame to/from ByteBuffer
 */
@ChannelHandler.Sharable
public class WsProxyControlHandler extends SimpleChannelInboundHandler<ControlMessage> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsProxyControlHandler.class);
    private static final int FRAME_LIMIT = 65535;

    private Channel mChannel;
    private EventLoopGroup mWorkerGroup;

    public WsProxyControlHandler(EventLoopGroup group) {
        sLogger.trace("<init>");
        mWorkerGroup = group;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, ControlMessage msg) throws Exception {
        if ("request".equalsIgnoreCase(msg.type) && "connect".equalsIgnoreCase(msg.action)) {
            Bootstrap bootstrap = new Bootstrap()
                    .group(mWorkerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            sLogger.debug("tunnel init");
                            //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print data in tunnel
                        }
                    });

            ChannelFuture future = (msg.address != null) ?
                    bootstrap.connect(msg.address, msg.port) :
                    bootstrap.connect(msg.domain, msg.port);
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    sLogger.debug("");
                    if (future.isSuccess()) {
                        sLogger.info("proxy {} - {}", ctx.channel().remoteAddress(), future.channel().remoteAddress());
                        ControlMessage msg = new ControlMessage();
                        msg.type = "response";
                        msg.action = "success";
                        ctx.writeAndFlush(msg);

                        ctx.pipeline().addLast(new WsProxyRelayReader(future.channel()));
                        future.channel().pipeline().addLast(new WsProxyRelayWriter(ctx.channel()));
                    } else {
                        ControlMessage msg = new ControlMessage();
                        msg.type = "response";
                        msg.action = "failure";
                        ctx.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE);
                    }
                }
            });
        } else if ("request".equalsIgnoreCase(msg.type) && "echo".equalsIgnoreCase(msg.action)) {
            msg.type = "response";
            ctx.writeAndFlush(msg);
        } else {
            sLogger.warn("Not supported message:{}", msg);
        }
    }

    @Override // SimpleChannelInboundHandler
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        sLogger.trace("event:{}", evt);
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete event = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            mChannel = ctx.channel();
            sLogger.info("upgrade {} subprotocol {}", mChannel.remoteAddress(), event.selectedSubprotocol());
        }
        if (mChannel != null) {
            mChannel.closeFuture()
                    .addListener(mCloseListener);
        }
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        sLogger.warn("connection exception\n", cause);
        if (mChannel.isActive()) {
            mChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    // FIXME:
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

    private ChannelFutureListener mCloseListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            sLogger.warn("connection lost {}", mChannel.remoteAddress());
        }
    };
}
