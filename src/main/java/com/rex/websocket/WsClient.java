package com.rex.websocket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

/**
 * WebSocket client
 * TODO: Support TLS
 */
public class WsClient {

    private static final Logger sLogger = LoggerFactory.getLogger(WsClient.class);

    private final Bootstrap mBootstrap;
    private ChannelFuture mChannelFuture;

    public WsClient() {
        sLogger.trace("");

        mBootstrap = new Bootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override // ChannelInitializer
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                                .addLast(new WsPathInterceptor());
                    }
                });
    }

    synchronized public void start(final SocketAddress address) {
        sLogger.trace("address:{}", address);
        if (mChannelFuture != null) {
            sLogger.warn("already started");
            return;
        }
        mChannelFuture = mBootstrap.connect(address)
                .syncUninterruptibly();
    }

    synchronized public void stop() {
        sLogger.trace("");
        if (mChannelFuture == null) {
            sLogger.warn("not started");
            return;
        }
        mChannelFuture.channel()
                .closeFuture()
                .syncUninterruptibly();
        mChannelFuture = null;
    }
}
