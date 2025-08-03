/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.rex.proxy.utils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.DomainWildcardMappingBuilder;
import io.netty.util.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Echoes back any received data from a client.
 * Customized from io.netty.example.echo.*
 */
public final class EchoServer {

    private static final Logger sLogger = LoggerFactory.getLogger(EchoServer.class);

    private EventLoopGroup mBossGroup;
    private EventLoopGroup mWorkerGroup;
    private ChannelFuture mServerFuture;

    private String mHost;
    private int mPort;

    public interface ChildListener {
        void onOpen(Channel ch);
        void onRead(Channel ch, Object msg);
        void onWrite(Channel ch, Object msg);
        void onClosed(Channel ch);
    }
    private ChildListener mChildListener;
    public EchoServer setChildListener(ChildListener listener) {
        mChildListener = listener;
        return this;
    }

    private final List<Channel> mChildChannelList = new ArrayList<>();
    private final ChannelFutureListener mChildCloseListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            sLogger.info("Channel {} closed", future.channel());
            synchronized (mChildChannelList) {
                mChildChannelList.remove(future.channel());
            }
            if (mChildListener != null) {
                mChildListener.onClosed(future.channel());
            }
        }
    };

    public EchoServer port(int port) {
        sLogger.trace("port={}", port);
        mPort = port;
        return this;
    }

    public EchoServer host(String host) {
        sLogger.trace("host=<{}>", host);
        mHost = host;
        return this;
    }

    public EchoServer start() throws Exception {
        return start(false);
    }

    public EchoServer start(boolean useSsl) throws Exception {
        sLogger.trace("useSsl={}", useSsl);
        // Configure SSL.
        final SslContext sslCtx;
        if (useSsl) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // Configure and start the server.
        mBossGroup = new NioEventLoopGroup(1);
        mWorkerGroup = new NioEventLoopGroup();
        mServerFuture = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        sLogger.trace("ch:{}", ch);
                        ChannelPipeline p = ch.pipeline();
                        if (sslCtx != null) {
                            if (mHost != null) {
                                Mapping<String, SslContext> mapping = new DomainWildcardMappingBuilder<>(sslCtx)
                                        .add(mHost, sslCtx)
                                        .build();
                                p.addLast(new SniHandler(mapping));
                            } else {
                                p.addLast(sslCtx.newHandler(ch.alloc()));
                            }
                        }
                        p.addLast(new LoggingHandler(LogLevel.DEBUG));
                        p.addLast(new EchoServerHandler(mChildListener));

                        synchronized (mChildChannelList) {
                            mChildChannelList.add(ch);
                        }
                        ch.closeFuture().addListener(mChildCloseListener);
                        if (mChildListener != null) {
                            mChildListener.onOpen(ch);
                        }
                    }
                })
                .bind(mPort)
                .sync();

        sLogger.info("Echo server started at port {}", port());
        return this;
    }

    public EchoServer stop() {
        sLogger.trace("");
        // Wait until the server socket is closed.
        if (! mServerFuture.isVoid()) {
            mServerFuture.channel()
                    .close()
                    .syncUninterruptibly();
        }
        synchronized (mChildChannelList) {
            for (Channel ch : mChildChannelList) {
                ch.writeAndFlush(Unpooled.EMPTY_BUFFER)
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }

        // Shut down all event loops to terminate all threads.
        mBossGroup.shutdownGracefully();
        mWorkerGroup.shutdownGracefully();
        sLogger.info("Echo server stopped");
        return this;
    }

    public int port() {
        return ((InetSocketAddress) mServerFuture.channel().localAddress()).getPort();
    }
}
