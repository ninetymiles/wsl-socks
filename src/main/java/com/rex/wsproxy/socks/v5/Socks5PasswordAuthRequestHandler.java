package com.rex.wsproxy.socks.v5;

import com.rex.wsproxy.WsProxyLocal;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class Socks5PasswordAuthRequestHandler extends SimpleChannelInboundHandler<Socks5PasswordAuthRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks5PasswordAuthRequestHandler.class);

    private final WsProxyLocal.Configuration mConfig;

    public Socks5PasswordAuthRequestHandler(WsProxyLocal.Configuration config) {
        mConfig = config;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks5PasswordAuthRequest request) throws Exception {
        sLogger.debug("PasswordAuthRequest");
        sLogger.trace("authUser:{} authPassword:{}", request.username(), request.password());
        if (request.username().equals(mConfig.authUser) && request.password().equals(mConfig.authPassword)) {
            sLogger.debug("Accepted");

            ctx.pipeline()
                    .addLast(new Socks5CommandRequestDecoder())
                    .addLast(new Socks5CommandRequestHandler(mConfig));

            sLogger.trace("Remove auth request decoder");
            ctx.pipeline().remove(Socks5PasswordAuthRequestDecoder.class);

            sLogger.trace("Remove auth request handler");
            ctx.pipeline().remove(this);

            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
        } else {
            sLogger.debug("Rejected");
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //sLogger.warn("Socks5PasswordAuthRequestHandler caught exception\n", cause);
        sLogger.warn("{}", cause.toString());
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
