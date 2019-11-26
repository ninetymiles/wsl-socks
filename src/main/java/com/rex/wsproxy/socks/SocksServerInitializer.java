package com.rex.wsproxy.socks;

import com.rex.wsproxy.WsProxyLocal;
import com.rex.wsproxy.socks.v5.Socks5InitialRequestHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                .addLast(Socks5ServerEncoder.DEFAULT)
                .addLast(new Socks5InitialRequestDecoder())
                .addLast(new Socks5InitialRequestHandler(mConfig));
    }
}
