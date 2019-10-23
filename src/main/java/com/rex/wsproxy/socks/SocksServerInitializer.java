package com.rex.wsproxy.socks;

import com.rex.wsproxy.WsProxyLocal;
import com.rex.wsproxy.socks.v4.Socks4CommandRequestHandler;
import com.rex.wsproxy.socks.v5.Socks5InitialRequestHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class SocksServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(SocksServerInitializer.class);

    private final WsProxyLocal.Configuration mConfig;

    public SocksServerInitializer(WsProxyLocal.Configuration config) {
        mConfig = config;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
        ch.pipeline()
                .addLast(new IdleStateHandler(0, 0, 900) { // Neither read nor write for 15min
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        sLogger.debug("Idle connection {}", ctx.channel().remoteAddress());
                        if (evt instanceof IdleStateEvent) {
                            ctx.close();
                        }
                    }
                })
                .addLast(new SocksPortUnificationServerHandler() {
                    @Override
                    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                        super.decode(ctx, in, out);
                        if (ctx.pipeline().last() instanceof Socks4ServerEncoder ||
                                ctx.pipeline().last() instanceof Socks4ServerDecoder) {
                            ctx.pipeline().addLast(new Socks4CommandRequestHandler());
                        }
                        if (ctx.pipeline().last() instanceof Socks5ServerEncoder ||
                                ctx.pipeline().last() instanceof Socks4ServerDecoder) {
                            ctx.pipeline().addLast(new Socks5InitialRequestHandler(mConfig.authUser, mConfig.authPassword));
                        }
                    }
                });
    }
}
