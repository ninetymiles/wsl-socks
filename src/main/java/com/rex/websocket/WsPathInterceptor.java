package com.rex.websocket;

import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.Utf8FrameValidator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle received http requests
 * Filter the specified path, upgrade to websocket handler
 */
@ChannelHandler.Sharable
public class WsPathInterceptor extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsPathInterceptor.class);

    private static final String PATH_WS = "/ws";

    public static final String SUBPROTOCOL = "com.rex.websocket.protocol.tunnel";

    public WsPathInterceptor() {
        sLogger.trace("");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        sLogger.trace("uri:<{}>", request.uri());
        if (request.uri().startsWith(PATH_WS)) {
            sLogger.debug("");

            ctx.pipeline()
                    .addLast(new WebSocketServerProtocolHandler(PATH_WS, SUBPROTOCOL, true))
                    .addLast(new Socks5ConnectionBuilder());

            request.retain();
            ctx.fireChannelRead(request);
            ctx.pipeline().remove(this);
            return;
        }

        sLogger.warn("invalid path {} from {}", request.uri(), ctx.channel().remoteAddress());
        ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.NOT_FOUND));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        sLogger.warn("server exception\n", cause);
    }

    @Sharable
    private static class Socks5ConnectionBuilder extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            sLogger.error("abandoning msg:{}", msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                WebSocketServerProtocolHandler.HandshakeComplete event = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
                if (event.selectedSubprotocol() == null) {
                    sLogger.error("select subprotocol failure");
                    return;
                }

                final Channel channel = ctx.channel();
                sLogger.info("subprotocol {} on {}", event.selectedSubprotocol(), channel.remoteAddress());

                channel.closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        sLogger.warn("connection died {}", future.channel().remoteAddress());
                    }
                });
                channel.pipeline()
                        .addLast(new WsTunnelConnection(channel))
                        .remove(this)
                        .remove(Utf8FrameValidator.class);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            sLogger.warn("caller exception:\n", cause);
        }
    }
}
