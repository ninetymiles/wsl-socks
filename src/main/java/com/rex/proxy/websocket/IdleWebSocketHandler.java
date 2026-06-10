package com.rex.proxy.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Handles idle timeout for pooled WebSocket connections.
 * Closes connections that have been idle for the specified duration.
 */
public class IdleWebSocketHandler extends IdleStateHandler {

    private static final Logger sLogger = LoggerFactory.getLogger(IdleWebSocketHandler.class);

    public IdleWebSocketHandler(long idleTimeSeconds) {
        super(0, 0, idleTimeSeconds, TimeUnit.SECONDS);
        sLogger.trace("<init> idleTimeSeconds={}", idleTimeSeconds);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        sLogger.info("WebSocket connection idle timeout {}, closing", ctx.channel());
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        sLogger.warn("IdleWebSocketHandler exception: {}", cause.toString());
        ctx.close();
    }
}
