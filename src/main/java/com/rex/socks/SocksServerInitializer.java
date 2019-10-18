package com.rex.socks;

import com.rex.SocksServer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SocksServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(SocksServerInitializer.class);

    private final SocksServer.Configuration mConfig;

    public SocksServerInitializer(SocksServer.Configuration config) {
        sLogger.trace("<init>");
        mConfig = config;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline()
                .addLast(new LoggingHandler(LogLevel.DEBUG))
                .addLast(new IdleStateHandler(0, 0, 900) { // Neither read nor write for 15min
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        sLogger.debug("Idle connection {}", ctx.channel().remoteAddress());
                        if (evt instanceof IdleStateEvent) {
                            ctx.close();
                        }
                    }
                })
                .addLast(new SocksPortUnificationServerHandler())
                .addLast(new SocksServerHandler(mConfig));
    }
}
