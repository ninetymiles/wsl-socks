package com.rex.websocket;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
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

    private final WsConnection.Callback mConnCallback;

    public WsPathInterceptor(WsConnection.Callback cb) {
        sLogger.trace("");
        mConnCallback = cb;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        sLogger.trace("uri:<{}>", request.uri());
        if (request.uri().startsWith(PATH_WS)) {
            sLogger.debug("upgrade protocol from {}", ctx.channel().remoteAddress());

            ctx.pipeline()
                    .addLast(new WebSocketServerProtocolHandler(PATH_WS, SUBPROTOCOL, true))
                    .addLast(new WsConnection(mConnCallback));

            sLogger.debug("upgrade connection:{}", ctx.pipeline().get(WsConnection.class));

            request.retain();
            ctx.fireChannelRead(request);
            ctx.pipeline().remove(this);
            return;
        }

        sLogger.warn("invalid path {} from {}", request.uri(), ctx.channel().remoteAddress());
        ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.NOT_FOUND));
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        sLogger.warn("server exception\n", cause);
    }
}
