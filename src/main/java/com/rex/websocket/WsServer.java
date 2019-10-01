package com.rex.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
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
 */
public class WsServer {

    private static final Logger sLogger = LoggerFactory.getLogger(WsServer.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2
    private final ChannelFuture mServerChannel;

    /**
     * Construct and start the server
     * @param address
     */
    public WsServer(final SocketAddress address) {
        sLogger.trace("address:{}", address);

        mServerChannel = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override // ChannelInitializer
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                                .addLast(new ChannelHandlerPathInterceptor());
                    }
                })
                .bind(address);
    }

    /**
     * Close the websocket server
     */
    public void close() {
        sLogger.trace("");
        mServerChannel.channel().close();
    }

    /**
     * Close the websocket server and wait for shutdown properly
     */
    public void closeSync() {
        sLogger.trace("");
        mServerChannel.channel().closeFuture().syncUninterruptibly();
    }
}
