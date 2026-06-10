package com.rex.proxy.websocket;

import com.rex.proxy.WslLocal;
import com.rex.proxy.websocket.control.ControlAuthBuilder;
import com.rex.proxy.websocket.control.ControlMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * Handle control messages from WebSocket server.
 * Note: Requires WsProxyControlCodec in the pipeline before this handler to convert TextWebSocketFrame to ControlMessage.
 */
public class WsClientHandler extends SimpleChannelInboundHandler<ControlMessage> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsClientHandler.class);

    private final Channel mSocksChannel; // Accepted socks client
    private final String mDstAddress;
    private final int mDstPort;
    private final String mSecret;
    private byte[] mNonce;
    private boolean mIsPooled; // Whether this is a pooled connection

    public WsClientHandler(Channel channel, String dstAddr, int dstPort, String secret) {
        this(channel, dstAddr, dstPort, secret, false);
    }

    public WsClientHandler(Channel channel, String dstAddr, int dstPort, String secret, boolean isPooled) {
        sLogger.trace("<init> dstAddr={} dstPort={} isPooled={}", dstAddr, dstPort, isPooled);
        mSocksChannel = channel;
        mDstAddress = dstAddr;
        mDstPort = dstPort;
        mSecret = secret;
        mIsPooled = isPooled;
    }

    @Override // ChannelInboundHandler
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        sLogger.trace("WsClientHandler added to pipeline, isPooled={}", mIsPooled);

        // If this is a pooled connection, the WebSocket is already authorized
        // Send connect request immediately without waiting for hello/authorization
        if (mIsPooled) {
            sLogger.debug("Pooled connection (already authorized), send connect request immediately");
            sendConnectRequest(ctx, false); // No token needed, connection is authorized
        }
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, ControlMessage response) throws Exception {
        sLogger.trace("read msg:{}", response);

        if ("response".equalsIgnoreCase(response.type)) {
            if ("success".equalsIgnoreCase(response.action)) {
                // Success
                sLogger.debug("Relay {} with {}", mSocksChannel, ctx.channel());
                //ctx.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print relayed data
                ctx.pipeline().addLast(new WsProxyWsToRaw(mSocksChannel));
                mSocksChannel.pipeline().addLast(new WsProxyRawToWs(ctx.channel()));

                //sLogger.trace("Remote channel:{} pipeline:{}", ctx.channel(), ctx.pipeline());
                //sLogger.trace("Local channel:{} pipeline:{}", mSocksChannel, mSocksChannel.pipeline());

                // XXX: Fire event on pipeline, make sure deliver to all the handlers
                ctx.pipeline().fireUserEventTriggered(WslLocal.RemoteStateEvent.REMOTE_READY);

                // Remove this handler after successful connection setup
                ctx.pipeline().remove(this);
            } else {
                // Failure
                sLogger.warn("WsClient got response {}", response.action);
                ctx.pipeline().fireUserEventTriggered(WslLocal.RemoteStateEvent.REMOTE_FAILED);

                // Close the socket immediately, avoid server left in TIME_WAIT state
                ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                        .addListener(ChannelFutureListener.CLOSE);
            }
        } else if ("authorized".equalsIgnoreCase(response.type)) {
            // Authorization successful, now send connect request
            sLogger.debug("Authorization successful, sending connect request");
            sendConnectRequest(ctx, false); // Don't include token since we're authorized
        } else if ("hello".equalsIgnoreCase(response.type)) {
            if (response.token != null) {
                mNonce = Base64.getDecoder().decode(response.token);
            }

            // If we have a secret and this is a new connection, send authorization first
            // For pooled connections, this won't be called (already authorized)
            if (mSecret != null && !mIsPooled) {
                sendAuthorization(ctx);
            } else {
                // No auth required or pooled connection, send connect request directly
                sendConnectRequest(ctx, mSecret != null && mIsPooled);
            }
        }
    }

    /**
     * Send authorization message to server (new protocol)
     */
    private void sendAuthorization(ChannelHandlerContext ctx) {
        ControlMessage authMsg = new ControlMessage();
        authMsg.type = "authorization";
        // Token = HMAC(secret, nonce)
        authMsg.token = new ControlAuthBuilder()
                .setSecret(mSecret)
                .setNonce(mNonce)
                .build();
        sLogger.trace("Send authorization: {}", authMsg.token);
        ctx.writeAndFlush(authMsg);
    }

    /**
     * Send connect request to WslServer
     */
    private void sendConnectRequest(ChannelHandlerContext ctx, boolean includeToken) {
        ControlMessage request = new ControlMessage();
        request.type = "request";
        request.action = "connect";
        request.address = mDstAddress;
        request.port = mDstPort;

        // Include token only for backward compatibility or if explicitly requested
        if (includeToken && mSecret != null && mNonce != null) {
            request.token = new ControlAuthBuilder()
                    .setSecret(mSecret)
                    .setNonce(mNonce)
                    .setAddress(mDstAddress)
                    .setPort(mDstPort)
                    .build();
        }

        sLogger.trace("Send connect request: address={} port={} hasToken={}", mDstAddress, mDstPort, request.token != null);
        ctx.writeAndFlush(request);
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("ClientHandler caught exception\n", cause);
        sLogger.warn("{}", cause.toString());
        ctx.close();
        //mOutput.close();
    }
}
