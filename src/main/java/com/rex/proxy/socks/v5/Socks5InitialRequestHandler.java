package com.rex.proxy.socks.v5;

import com.rex.proxy.WslLocal;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class Socks5InitialRequestHandler extends SimpleChannelInboundHandler<Socks5InitialRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks5InitialRequestHandler.class);

    private final WslLocal.Configuration mConfig;

    public Socks5InitialRequestHandler(WslLocal.Configuration config) {
        mConfig = config;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks5InitialRequest request) throws Exception {
        sLogger.debug("InitialRequest");
        if (mConfig.authUser != null && mConfig.authPassword != null) {
            ctx.pipeline()
                    .addLast(new Socks5PasswordAuthRequestDecoder())
                    .addLast(new Socks5PasswordAuthRequestHandler(mConfig));
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
        } else {
            ctx.pipeline()
                    .addLast(new Socks5CommandRequestDecoder())
                    .addLast(new Socks5CommandRequestHandler(mConfig));
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
        }

        sLogger.trace("Remove initial request decoder");
        ctx.pipeline().remove(Socks5InitialRequestDecoder.class);

        sLogger.trace("Remove initial request handler");
        ctx.pipeline().remove(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //sLogger.warn("Socks5InitialRequestHandler caught exception\n", cause);
        sLogger.warn("{}", cause.toString());
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
