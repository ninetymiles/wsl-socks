package com.rex.socks.v5;

import com.rex.socks.SocksUtils;
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

    private String mAuthUser;
    private String mAuthPassword;

    public Socks5PasswordAuthRequestHandler(String authUser, String authPassword) {
        mAuthUser = authUser;
        mAuthPassword = authPassword;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Socks5PasswordAuthRequest request) throws Exception {
        sLogger.debug("PasswordAuthRequest");
        sLogger.trace("authUser:{} authPassword:{}", request.username(), request.password());
        if (request.username().equals(mAuthUser) && request.password().equals(mAuthPassword)) {
            sLogger.debug("Accepted");

            ctx.pipeline()
                    .addLast(new Socks5CommandRequestDecoder())
                    .addLast(new Socks5CommandRequestHandler());

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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        SocksUtils.closeOnFlush(ctx.channel());
    }
}
