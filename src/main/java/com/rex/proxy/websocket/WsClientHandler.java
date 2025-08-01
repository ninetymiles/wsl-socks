package com.rex.proxy.websocket;

import com.google.gson.Gson;
import com.rex.proxy.websocket.control.ControlAuthBuilder;
import com.rex.proxy.websocket.control.ControlMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * Receive BinaryWebSocketFrame from websocket channel, write to raw socket channel as ByteBuf
 */
public class WsClientHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsClientHandler.class);

    private final Channel mSocksChannel; // Accepted socks client
    private final Gson mGson = new Gson();
    private final String mDstAddress;
    private final int mDstPort;
    private final ResponseListener mListener;
    private final String mSecret;
    private byte[] mNonce;

    public interface ResponseListener {
        void onResponse(boolean success);
    }

    public WsClientHandler(Channel channel, String dstAddr, int dstPort, String secret, ResponseListener listener) {
        sLogger.trace("<init>");
        mSocksChannel = channel;
        mDstAddress = dstAddr;
        mDstPort = dstPort;
        mSecret = secret;
        mListener = listener;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        sLogger.trace("read msg:{}", msg);
        ControlMessage response = mGson.fromJson(msg.text(), ControlMessage.class);
        if ("response".equalsIgnoreCase(response.type)) {
            if ("success".equalsIgnoreCase(response.action)) {
                // Success
                if (mListener != null) {
                    mListener.onResponse(true);
                }

                sLogger.debug("Relay {} with {}", mSocksChannel, ctx.channel());
                //ctx.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print relayed data
                ctx.pipeline().addLast(new WsProxyWsToRaw(mSocksChannel));
                mSocksChannel.pipeline().addLast(new WsProxyRawToWs(ctx.channel()));

                sLogger.trace("FINAL pipeline:{}", mSocksChannel.pipeline());
            } else {
                // Failure
                sLogger.warn("WsClient got response {}", response.action);
                if (mListener != null) {
                    mListener.onResponse(false);
                }

                // Close the socket immediately, avoid server left in TIME_WAIT state
                ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }

        if ("hello".equalsIgnoreCase(response.type)) {
            if (response.token != null) {
                mNonce = Base64.getDecoder().decode(response.token);
            }

            ControlMessage request = new ControlMessage();
            request.type = "request";
            request.action = "connect";
            request.address = mDstAddress;
            request.port = mDstPort;
            if (mSecret != null) {
                request.token = new ControlAuthBuilder()
                        .setSecret(mSecret)
                        .setNonce(mNonce)
                        .setAddress(mDstAddress)
                        .setPort(mDstPort)
                        .build();
            }
            sLogger.trace("request:{}", request);
            ctx.writeAndFlush(new TextWebSocketFrame(mGson.toJson(request)));
        }
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("ClientHandler caught exception\n", cause);
        sLogger.warn("{}", cause.toString());
        ctx.close();
        //mOutput.close();
    }
}
