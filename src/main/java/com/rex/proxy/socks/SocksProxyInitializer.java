package com.rex.proxy.socks;

import com.rex.proxy.WslLocal;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SelectableChannel;

/**
 * Initialize the client channel pipeline
 * WsProxyLocal (Socks Server) will use this initializer to bridge the socks client with internet connection directly
 */
public class SocksProxyInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(SocksProxyInitializer.class);

    private final WslLocal.Configuration mConfig;
    private final ChannelHandlerContext mContext; // Accepted socks client

    public SocksProxyInitializer(final WslLocal.Configuration config, final ChannelHandlerContext ctx) {
        sLogger.trace("<init>");
        mConfig = config;
        mContext = ctx;
    }

    @Override // ChannelInitializer
    protected void initChannel(final SocketChannel ch) throws Exception {
        sLogger.trace("initChannel ch={}", ch);
        if (ch instanceof NioSocketChannel) {
            SelectableChannel sc = ((NioSocketChannel) ch).unsafe().ch();
            //sLogger.trace("NioSocketChannel selectableChannel:{}", sc.getClass());
            if (sc instanceof java.nio.channels.SocketChannel) {
                //sLogger.trace("java.nio.channels.SocketChannel socket:{}", ((java.nio.channels.SocketChannel) sc).socket());
                if (mConfig.callback != null) {
                    mConfig.callback.onConnect(((java.nio.channels.SocketChannel) sc).socket());
                }
            }
        }

        // XXX: SocketChannel is like [id: 0xa8246527], no address and port info
        // The address info will be available in bootstrap connect future
        sLogger.debug("Relay {} with {}", mContext.channel(), ch);
        ch.pipeline()
                //.addLast(new LoggingHandler(LogLevel.DEBUG)) // Print relayed data
                .addLast(new RelayHandler(mContext.channel()))
                .addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        super.channelActive(ctx);
                        sLogger.trace("channel={}", ctx.channel());
                        ctx.pipeline().remove(this);
                        ctx.pipeline().fireUserEventTriggered(WslLocal.RemoteStateEvent.REMOTE_READY);
                    }
                });
        //sLogger.trace("Remote pipeline:{}", ch.pipeline());

        ch.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                sLogger.debug("Socks peer closed {}", future.channel());
                sLogger.debug("Socks force close {}", mContext.channel());
                if (mContext.channel().isActive()) {
                    mContext.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                            .addListener(ChannelFutureListener.CLOSE);
                }
            }
        });

        //mContext.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print relayed data
        mContext.pipeline().addLast(new RelayHandler(ch));
        //sLogger.trace("Local pipeline:{}", mContext.pipeline());

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
