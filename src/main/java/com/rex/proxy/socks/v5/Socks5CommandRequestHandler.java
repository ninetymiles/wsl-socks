package com.rex.proxy.socks.v5;

import com.rex.proxy.WslLocal;
import com.rex.proxy.socks.SocksBindInitializer;
import com.rex.proxy.socks.SocksProxyInitializer;
import com.rex.proxy.websocket.WsClientHandler;
import com.rex.proxy.websocket.WsClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.NoSuchElementException;

@ChannelHandler.Sharable
public final class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks5CommandRequestHandler.class);

    private final WslLocal.Configuration mConfig;

    public Socks5CommandRequestHandler(WslLocal.Configuration config) {
        sLogger.trace("<init>");
        mConfig = config;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Socks5CommandRequest request) throws Exception {
        sLogger.debug("CommandRequest {} dstAddrType={} dstAddr={}:{}", request.type(), request.dstAddrType(), request.dstAddr(), request.dstPort());

        sLogger.trace("Remove command request decoder");
        try {
            ctx.pipeline().remove(Socks5CommandRequestDecoder.class);
        } catch (NoSuchElementException ex) {
            // test case will assemble without decoder
        }

        sLogger.trace("Remove command request handler");
        ctx.pipeline().remove(this);

        if (Socks5CommandType.CONNECT.equals(request.type())) {
            Bootstrap bootstrap = new Bootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                    .option(ChannelOption.SO_KEEPALIVE, true);

            if (mConfig.proxyUri != null) {
                String dstAddr = mConfig.proxyUri.getHost();
                int dstPort = mConfig.proxyUri.getPort();
                if (dstPort == -1) {
                    if ("wss".equalsIgnoreCase(mConfig.proxyUri.getScheme())) {
                        dstPort = 443;
                    } else {
                        dstPort = 80;
                    }
                }
                sLogger.debug("Proxy tunnel to {}:{}", dstAddr, dstPort);

                WsClientHandler.ResponseListener responseListener = new WsClientHandler.ResponseListener() {
                    @Override
                    public void onResponse(boolean success) {
                        sLogger.trace("success:{}", success);
                        if (! ctx.channel().isActive()) {
                            return;
                        }
                        if (success) {
                            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4));

                            sLogger.trace("Remove socks5 server encoder");
                            ctx.pipeline().remove(Socks5ServerEncoder.class);

                            sLogger.trace("FINAL pipeline:{}", ctx.pipeline());
                        } else {
                            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4))
                                    .addListener(ChannelFutureListener.CLOSE);
                        }
                    }
                };
                bootstrap.handler(new WsClientInitializer(mConfig, ctx, request.dstAddr(), request.dstPort(), responseListener))
                        .connect(dstAddr, dstPort);
            } else {
                sLogger.debug("Proxy direct to {}:{}", request.dstAddr(), request.dstPort());
                bootstrap.handler(new SocksProxyInitializer(mConfig, ctx))
                        .connect(request.dstAddr(), request.dstPort())
                        .addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                sLogger.trace("isSuccess:{}", future.isSuccess());
                                if (! ctx.channel().isActive()) {
                                    return;
                                }
                                if (future.isSuccess()) {
                                    ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4));

                                    sLogger.trace("Remove socks5 server encoder");
                                    ctx.pipeline().remove(Socks5ServerEncoder.class);

                                    sLogger.trace("FINAL pipeline:{}", ctx.pipeline());
                                } else {
                                    ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4))
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                            }
                        });
            }
        } else if (Socks5CommandType.BIND.equals(request.type())) {
            // 1st, Setup server socket on addr_a port_a
            // 2nd, Send CommandResponse with addr_a port_a when bind success
            // 3th, Send CommandResponse with addr_b port_b when accept connection from addr_b port_b
            // 4th, Relay traffics
            final ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new SocksBindInitializer(mConfig, ctx))
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            final InetSocketAddress addr = new InetSocketAddress(0);
            final ChannelFuture future = bootstrap.bind(addr);
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    InetSocketAddress sockAddr = (InetSocketAddress) future.channel().localAddress();
                    Socks5AddressType type = Socks5AddressType.IPv4;
                    if (sockAddr.getAddress() instanceof Inet6Address) {
                        type = Socks5AddressType.IPv6;
                    }
                    sLogger.debug("Bind address:{}", sockAddr);
                    ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, type, sockAddr.getAddress().getHostAddress(), sockAddr.getPort()));
                }
            });

            // Client socket closed will auto stop the server socket
            ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) throws Exception {
                    sLogger.debug("BIND channel closed");
                    future.channel().close();
                }
            });
        } else if (Socks5CommandType.UDP_ASSOCIATE.equals(request.type())) {
            // 1st, Client register addr_a and port_a, server will receive from addr_a:port_a only
            //      If client register with 0.0.0.0:0, server will skip the client address limit, for support client behind NAT
            // 2st, Server bind UDP on random port and send CommandResponse for client with binded address and port
            // 3rd, Client send UDP
            // If TCP connection closed, will stop the UDP relay
            // If bind failed, server should close the TCP connection shortly after send FAILURE
            // Currently force ignore the addr_a and port_a for supporting
            final Bootstrap bootstrap = new Bootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new Socks5UdpRelayMessageEncoder())
                                    .addLast(new Socks5UdpRelayMessageDecoder())
                                    .addLast(new Socks5UdpRelayHandler(ctx.channel()));
                        }
                    });
            final ChannelFuture future = bootstrap.bind(new InetSocketAddress(0));
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        InetSocketAddress sockAddr = (InetSocketAddress) future.channel().localAddress();
                        Socks5AddressType type = Socks5AddressType.IPv4;
                        if (sockAddr.getAddress() instanceof Inet6Address) {
                            type = Socks5AddressType.IPv6;
                        }
                        sLogger.debug("Associate UDP address:{}", sockAddr);
                        ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, type, sockAddr.getAddress().getHostAddress(), sockAddr.getPort()));
                    } else {
                        sLogger.debug("Associate UDP failed");
                        ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4))
                                .addListener(ChannelFutureListener.CLOSE);
                    }
                }
            });
            ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) throws Exception {
                    sLogger.debug("UDP_ASSOCIATE channel closed");
                    future.channel().close();
                }
            });
        } else {
            sLogger.warn("Unsupported command type:{}", request.type());
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.IPv4))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("{}\n", ctx.channel(), cause); // For debugging
        sLogger.warn("{} - {}", ctx.channel(), cause.getMessage());
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
