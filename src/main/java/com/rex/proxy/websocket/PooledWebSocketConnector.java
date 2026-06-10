package com.rex.proxy.websocket;

import com.rex.proxy.WslLocal;
import com.rex.proxy.websocket.control.WsProxyControlCodec;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to manage pooled WebSocket connections.
 * Handles acquiring a connection from the pool, sending connect request,
 * and setting up data forwarding handlers.
 */
public class PooledWebSocketConnector {

    private static final Logger sLogger = LoggerFactory.getLogger(PooledWebSocketConnector.class);

    /**
     * Connect to target address using a pooled WebSocket connection.
     *
     * @param dstAddr Destination address
     * @param dstPort Destination port
     * @param localCtx Local socket context (SOCKS or HTTP)
     * @param config WslLocal configuration
     * @param stateHandler Handler to receive remote state events
     */
    public static void connect(
            String dstAddr,
            int dstPort,
            ChannelHandlerContext localCtx,
            WslLocal.Configuration config,
            SimpleUserEventChannelHandler<WslLocal.RemoteStateEvent> stateHandler) {

        sLogger.debug("Connect to {}:{} using pooled WebSocket", dstAddr, dstPort);

        // Acquire a WebSocket channel from the pool
        Future<Channel> acquireFuture = WebSocketChannelPoolManager.getInstance()
                .acquire(dstAddr, dstPort, localCtx, config);

        acquireFuture.addListener(new GenericFutureListener<Future<Channel>>() {
            @Override
            public void operationComplete(Future<Channel> future) throws Exception {
                if (!future.isSuccess()) {
                    sLogger.warn("Failed to acquire WebSocket channel: {}", future.cause().toString());
                    // Notify failure
                    localCtx.pipeline().fireUserEventTriggered(WslLocal.RemoteStateEvent.REMOTE_FAILED);
                    return;
                }

                Channel wsChannel = future.getNow();
                sLogger.debug("Acquired WebSocket channel {}", wsChannel);

                // Add codec and state event handler to WebSocket pipeline
                wsChannel.pipeline()
                        .addLast("controlCodec", new WsProxyControlCodec())
                        .addLast("stateHandler", stateHandler);

                // Send connect request to WslServer
                // The WsClientHandler will handle the protocol handshake
                // Pass isPooled=true to indicate this is a pooled connection
                WsClientHandler clientHandler = new WsClientHandler(localCtx.channel(), dstAddr, dstPort, config.proxyUid, true);
                wsChannel.pipeline().addLast("wsClientHandler", clientHandler);

                // Set up cleanup when local socket closes
                localCtx.channel().closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        sLogger.debug("Local socket closed, returning WebSocket to pool");

                        // Return the WebSocket channel to pool
                        // This will clean up handlers and keep the connection alive
                        WebSocketChannelPoolManager.getInstance().release(wsChannel, dstAddr, dstPort);
                    }
                });

                // Set up cleanup when WebSocket closes unexpectedly
                wsChannel.closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        sLogger.warn("WebSocket connection lost {}", future.channel().remoteAddress());
                        // Close local socket if it's still active
                        if (localCtx.channel().isActive()) {
                            localCtx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                                    .addListener(ChannelFutureListener.CLOSE);
                        }
                    }
                });
            }
        });
    }
}
