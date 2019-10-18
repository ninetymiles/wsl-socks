package com.rex.socks;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {

    private static final Logger sLogger = LoggerFactory.getLogger(SocksServerConnectHandler.class);

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) throws Exception {
        if (message instanceof Socks4CommandRequest) {
            final Socks4CommandRequest request = (Socks4CommandRequest) message;
            final Channel inboundChannel = ctx.channel();
            new Bootstrap().group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            sLogger.debug("Relay init");
                            //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print data in tunnel
                        }
                    })
                    .connect(request.dstAddr(), request.dstPort())
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                ctx.channel().writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS));
                                ctx.pipeline().remove(SocksServerConnectHandler.this);

                                sLogger.info("Forward {} to {}", ctx.channel().localAddress(), future.channel().remoteAddress());
                                ctx.pipeline().addLast(new RelayHandler(future.channel()));

                                sLogger.info("Reverse {} to {}", future.channel().remoteAddress(), ctx.channel().localAddress());
                                future.channel().pipeline().addLast(new RelayHandler(ctx.channel()));
                            } else {
                                if (ctx.channel().isActive()) {
                                    ctx.channel().writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED))
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                            }
                        }
                    });
        } else if (message instanceof Socks5CommandRequest) {
            final Socks5CommandRequest request = (Socks5CommandRequest) message;
            final Channel inboundChannel = ctx.channel();
            new Bootstrap().group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            sLogger.debug("Relay init");
                            //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print data in tunnel
                        }
                    })
                    .connect(request.dstAddr(), request.dstPort()).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4));
                                ctx.pipeline().remove(SocksServerConnectHandler.this);

                                sLogger.info("Forward {} to {}", ctx.channel().localAddress(), future.channel().remoteAddress());
                                ctx.pipeline().addLast(new RelayHandler(future.channel()));

                                sLogger.info("Reverse {} to {}", future.channel().remoteAddress(), ctx.channel().localAddress());
                                future.channel().pipeline().addLast(new RelayHandler(ctx.channel()));
                            } else {
                                if (ctx.channel().isActive()) {
                                    ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4))
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                            }
                        }
                    });
        } else {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SocksUtils.closeOnFlush(ctx.channel());
    }
}
