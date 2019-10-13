package com.rex.socks;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public final class SocksServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "1080"));

    private ChannelFuture mFuture;
    private EventLoopGroup mBossGroup;
    private EventLoopGroup mWorkerGroup;
    private ChannelFutureListener mCloseListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            synchronized (SocksServer.this) {
                if (mBossGroup != null) {
                    mBossGroup.shutdownGracefully();
                }
                if (mWorkerGroup != null) {
                    mWorkerGroup.shutdownGracefully();
                }
            }
        }
    };

    synchronized public SocksServer start() {
        mBossGroup = new NioEventLoopGroup(1);
        mWorkerGroup = new NioEventLoopGroup();
        mFuture = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new SocksServerInitializer())
                .bind(PORT);
        mFuture.channel()
                .closeFuture()
                .addListener(mCloseListener);
        return this;
    }

    synchronized public SocksServer stop() {
        mFuture.channel().close();
        return this;
    }

    synchronized public SocksServer waitForClose() throws InterruptedException {
        mFuture.channel()
                .closeFuture()
                .sync();
        return this;
    }

    public int port() {
        return PORT;
    }

    public static void main(String[] args) throws Exception {
        new SocksServer().start();
    }
}
