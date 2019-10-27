package com.rex.wsproxy.websocket;

import com.google.gson.Gson;
import com.rex.wsproxy.websocket.control.ControlMessage;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receive BinaryWebSocketFrame from websocket channel, write to raw socket channel as ByteBuf
 */
public class WsClientHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsClientHandler.class);

    private final Channel mOutput; // Accepted socks client
    private final Gson mGson = new Gson();
    private String mDstAddress;
    private int mDstPort;

    public WsClientHandler(Channel channel, String dstAddr, int dstPort) {
        sLogger.trace("<init>");
        mOutput = channel;
        mDstAddress = dstAddr;
        mDstPort = dstPort;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        sLogger.trace("read msg:{}", msg);
        ControlMessage response = mGson.fromJson(msg.text(), ControlMessage.class);
        if ("response".equalsIgnoreCase(response.type)) {
            if ("success".equalsIgnoreCase(response.action)) {
                // Success
                // FIXME: Should support socks4
                mOutput.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4));

                sLogger.trace("Remove socks5 server encoder");
                mOutput.pipeline().remove(Socks5ServerEncoder.class);

                sLogger.debug("Relay {} with {}", mOutput, ctx.channel());
                //ctx.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print relayed data
                ctx.pipeline().addLast(new WsProxyRelayReader(mOutput));
                mOutput.pipeline().addLast(new WsProxyRelayWriter(ctx.channel()));

                sLogger.trace("FINAL channels:{}", mOutput.pipeline());
            } else {
                // Failure
                if (mOutput.isActive()) {
                    mOutput.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4))
                            .addListener(ChannelFutureListener.CLOSE);
                }
            }
        }
    }

    @Override // SimpleChannelInboundHandler
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        sLogger.trace("event:{}", evt);

        if (WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE.equals(evt)) {
            Channel channel = ctx.channel();
            sLogger.info("client connection {} - {}", channel.localAddress(), channel.remoteAddress());

            ControlMessage msg = new ControlMessage();
            msg.type = "request";
            msg.action = "connect";
            msg.address = mDstAddress;
            msg.port = mDstPort;

            sLogger.trace("request:{}", msg);
            channel.writeAndFlush(new TextWebSocketFrame(mGson.toJson(msg)));

            channel.closeFuture()
                    .addListener(mCloseListener);
        }
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("connection exception\n", cause);
        ctx.close();
        mOutput.close();
    }

    private ChannelFutureListener mCloseListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            sLogger.warn("connection closed");
            mOutput.close();
        }
    };
}
