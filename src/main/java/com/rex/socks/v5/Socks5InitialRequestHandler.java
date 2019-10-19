package com.rex.socks.v5;

import com.rex.socks.SocksUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class Socks5InitialRequestHandler extends SimpleChannelInboundHandler<Socks5InitialRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks5InitialRequestHandler.class);

    private final String mAuthUser;
    private final String mAuthPassword;

    public Socks5InitialRequestHandler(String authUser, String authPassword) {
        mAuthUser = authUser;
        mAuthPassword = authPassword;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks5InitialRequest request) throws Exception {
        sLogger.debug("InitialRequest");
        if (mAuthUser != null && mAuthPassword != null) {
            ctx.pipeline()
                    .addLast(new Socks5PasswordAuthRequestDecoder())
                    .addLast(new Socks5PasswordAuthRequestHandler(mAuthUser, mAuthPassword));
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
        } else {
            ctx.pipeline()
                    .addLast(new Socks5CommandRequestDecoder())
                    .addLast(new Socks5CommandRequestHandler());
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
        }

        sLogger.trace("Remove initial request decoder");
        ctx.pipeline().remove(Socks5InitialRequestDecoder.class);

        sLogger.trace("Remove initial request handler");
        ctx.pipeline().remove(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        SocksUtils.closeOnFlush(ctx.channel());
    }
}
