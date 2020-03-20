package com.rex.proxy.socks;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialize the client channel pipeline
 * WsProxyLocal (Socks Server) will use this initializer to bridge the socks client with internet connection directly
 */
public class SocksProxyInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(SocksProxyInitializer.class);

    private final ChannelHandlerContext mContext; // Accepted socks client

    public SocksProxyInitializer(final ChannelHandlerContext ctx) {
        sLogger.trace("<init>");
        mContext = ctx;
    }

    @Override // ChannelInitializer
    protected void initChannel(final SocketChannel ch) throws Exception {
        sLogger.trace("initChannel");

        // XXX: SocketChannel is like [id: 0xa8246527], no address and port info
        // The address info will be available in bootstrap connect future
        sLogger.debug("Relay {} with {}", mContext.channel(), ch);
        //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print relayed data
        ch.pipeline().addLast(new RelayHandler(mContext.channel()));
        ch.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                sLogger.debug("Socks peer closed {}", future.channel());
                sLogger.debug("Socks force close {}", mContext.channel());
                mContext.close();
            }
        });

        mContext.pipeline().addLast(new RelayHandler(ch));
        mContext.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                sLogger.debug("Socks local closed {}", future.channel());
                sLogger.debug("Socks force close {}", ch);
                if (ch.isActive()) {
                    ch.writeAndFlush(Unpooled.EMPTY_BUFFER)
                            .addListener(ChannelFutureListener.CLOSE);
                }
            }
        });
    }
}
