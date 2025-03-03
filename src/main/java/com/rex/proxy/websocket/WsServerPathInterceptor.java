package com.rex.proxy.websocket;

import com.rex.proxy.WslServer;
import com.rex.proxy.websocket.control.WsProxyControlCodec;
import io.netty.channel.*;
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
public class WsServerPathInterceptor extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsServerPathInterceptor.class);

    private static final String WS_SUBPROTOCOL = "com.rex.websocket.protocol.proxy2";

    private final EventLoopGroup mWorkerGroup;
    private final WslServer.Configuration mConfig;

    public WsServerPathInterceptor(EventLoopGroup group, WslServer.Configuration config) {
        sLogger.trace("<init>");
        mWorkerGroup = group;
        mConfig = config;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        sLogger.trace("uri:<{}> proxyPath:<{}>", request.uri(), mConfig.proxyPath);
        if (mConfig.proxyPath == null || request.uri().startsWith(mConfig.proxyPath)) {
            sLogger.debug("channel {} handshaker websocket", ctx.channel().remoteAddress());

            ctx.pipeline()
                    .addLast(new WebSocketServerProtocolHandler(request.uri(), WS_SUBPROTOCOL, true))
                    .addLast(new SimpleUserEventChannelHandler<WebSocketServerProtocolHandler.HandshakeComplete>() {
                        @Override
                        protected void eventReceived(ChannelHandlerContext ctx, WebSocketServerProtocolHandler.HandshakeComplete evt) throws Exception {
                            sLogger.info("channel {} handshake <{}> complete", ctx.channel().remoteAddress(), evt.selectedSubprotocol());
                            ctx.pipeline()
                                    .addLast(new WsProxyControlCodec())
                                    .addLast(new WsProxyControlHandler(mWorkerGroup, mConfig))
                                    .remove(WsServerPathInterceptor.this)
                                    .remove(this);
                            sLogger.trace("pipeline:{}", ctx.pipeline());
                        }
                    });
            sLogger.trace("pipeline:{}", ctx.pipeline());

            ctx.fireChannelRead(request.retain());
            return;
        }

        sLogger.warn("invalid path {} from {}", request.uri(), ctx.channel().remoteAddress());
        ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.NOT_FOUND))
            .addListener(ChannelFutureListener.CLOSE);
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("WsServerPathInterceptor caught exception\n", cause);
        sLogger.warn("{}", cause.toString());
        ctx.close();
    }
}
