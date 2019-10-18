package com.rex.socks;

import com.rex.SocksServer;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private static final Logger sLogger = LoggerFactory.getLogger(SocksServerHandler.class);

    private final SocksServer.Configuration mConfig;

    public SocksServerHandler(SocksServer.Configuration config) {
        sLogger.trace("<init>");
        mConfig = config;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) throws Exception {
        switch (socksRequest.version()) {
        case SOCKS4a:
            Socks4CommandRequest socks4CmdRequest = (Socks4CommandRequest) socksRequest;
            if (socks4CmdRequest.type() == Socks4CommandType.CONNECT) {
                sLogger.debug("Command{} {}", socks4CmdRequest.version(), socks4CmdRequest.type());
                ctx.pipeline()
                        .addLast(new SocksServerConnectHandler())
                        .remove(this);
                ctx.fireChannelRead(socksRequest);
            } else {
                ctx.close();
            }
            break;
        case SOCKS5:
            if (socksRequest instanceof Socks5InitialRequest) {
                sLogger.debug("Initial ver:{}", socksRequest.version());
                if (mConfig.authUser != null && mConfig.authPassword != null) {
                    ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                    ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
                } else {
                    ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                    ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
                }
            } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
                Socks5PasswordAuthRequest authRequest = (Socks5PasswordAuthRequest) socksRequest;
                sLogger.debug("PasswordAuth");
                sLogger.trace("authUser:{} authPassword:{}", authRequest.username(), authRequest.password());
                if (authRequest.username().equals(mConfig.authUser) && authRequest.password().equals(mConfig.authPassword)) {
                    sLogger.debug("Accepted");
                    ctx.pipeline()
                            .addFirst(new Socks5CommandRequestDecoder())
                            .remove(Socks5PasswordAuthRequestDecoder.class);
                    ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                } else {
                    sLogger.debug("Rejected");
                    ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                            .addListener(ChannelFutureListener.CLOSE);
                }
            } else if (socksRequest instanceof Socks5CommandRequest) {
                Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest) socksRequest;
                sLogger.debug("Command{} {}", socks5CmdRequest.version(), socks5CmdRequest.type());
                if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                    ctx.pipeline()
                            .addLast(new SocksServerConnectHandler())
                            .remove(this);
                    ctx.fireChannelRead(socksRequest);
                } else {
                    sLogger.warn("Unsupported command type:{}", socks5CmdRequest.type());
                    ctx.close();
                }
            } else {
                ctx.close();
            }
            break;
        case UNKNOWN:
        default:
            sLogger.warn("Unknown version {}", socksRequest.version());
            ctx.close();
            break;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        SocksUtils.closeOnFlush(ctx.channel());
    }
}
