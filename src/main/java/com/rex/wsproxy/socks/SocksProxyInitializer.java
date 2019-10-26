package com.rex.wsproxy.socks;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialize the client channel pipeline
 * WsProxyLocal (Socks Server) will use this initializer to handshake with WsProxyServer (WebSocket Server)
 */
public class SocksProxyInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(SocksProxyInitializer.class);

    private final ChannelHandlerContext mContext;

    public SocksProxyInitializer(final ChannelHandlerContext ctx) {
        sLogger.trace("<init>");
        mContext = ctx;
    }

    @Override // ChannelInitializer
    protected void initChannel(SocketChannel ch) throws Exception {
        sLogger.trace("initChannel");

        sLogger.debug("Relay {} with {}", mContext.channel(), ch);
        //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print relayed data
        ch.pipeline().addLast(new RelayHandler(mContext.channel()));
        mContext.pipeline().addLast(new RelayHandler(ch));
    }
}
