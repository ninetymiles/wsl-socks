package com.rex.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

/**
 * WebSocket server
 * TODO: Support TLS
 */
public class WsServer {

    private static final Logger sLogger = LoggerFactory.getLogger(WsServer.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2

    private final ServerBootstrap mBootstrap;
    private ChannelFuture mChannelFuture;

    /**
     * Construct the server
     */
    public WsServer() {
        sLogger.trace("");

        mBootstrap = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override // ChannelInitializer
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                                .addLast(new WsPathInterceptor());
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true);
    }

    /**
     * Start the websocket server
     *
     * @param address
     */
    synchronized public void start(final SocketAddress address) {
        sLogger.trace("address:{}", address);
        if (mChannelFuture != null) {
            sLogger.warn("already started");
            return;
        }
        mChannelFuture = mBootstrap.bind(address)
                .syncUninterruptibly();
    }

    /**
     * Stop the websocket server
     */
    synchronized public void stop() {
        sLogger.trace("");
        if (mChannelFuture == null) {
            sLogger.warn("not started");
            return;
        }
        mChannelFuture.channel().close();
        mChannelFuture.channel().closeFuture().syncUninterruptibly();
        mChannelFuture = null;
    }
}
