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
package com.rex.utils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Echoes back any received data from a client.
 * Customized from io.netty.example.echo.*
 */
public final class EchoServer {

    public static final int PORT = 8007;

    private EventLoopGroup mBossGroup;
    private EventLoopGroup mWorkerGroup;
    private ChannelFuture mServerFuture;

    public EchoServer start(boolean useSsl) throws Exception {
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
                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        if (sslCtx != null) {
                            p.addLast(sslCtx.newHandler(ch.alloc()));
                        }
                        //p.addLast(new LoggingHandler(LogLevel.INFO));
                        p.addLast(new EchoServerHandler());
                    }
                })
                .bind(PORT).sync();
        return this;
    }

    public EchoServer stop() {
        // Wait until the server socket is closed.
        if (! mServerFuture.isVoid()) {
            mServerFuture.channel()
                    .close()
                    .syncUninterruptibly();
        }
        // Shut down all event loops to terminate all threads.
        mBossGroup.shutdownGracefully();
        mWorkerGroup.shutdownGracefully();
        return this;
    }
}
