package com.rex.wsproxy.socks.v5;

import com.rex.wsproxy.WsProxyLocal;
import com.rex.wsproxy.socks.SocksProxyInitializer;
import com.rex.wsproxy.websocket.WsClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks5CommandRequestHandler.class);

    private WsProxyLocal.Configuration mConfig;

    public Socks5CommandRequestHandler(WsProxyLocal.Configuration config) {
        sLogger.trace("<init>");
        mConfig = config;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Socks5CommandRequest request) throws Exception {
        if (Socks5CommandType.CONNECT.equals(request.type())) {
            sLogger.debug("CommandRequest {} {}:{}", request.type(), request.dstAddr(), request.dstPort());

            Bootstrap bootstrap = new Bootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future;
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
                bootstrap.handler(new WsClientInitializer(mConfig, ctx, request.dstAddr(), request.dstPort()))
                        .connect(dstAddr, dstPort);
            } else {
                sLogger.debug("Proxy direct to {}:{}", request.dstAddr(), request.dstPort());
                bootstrap.handler(new SocksProxyInitializer(ctx))
                        .connect(request.dstAddr(), request.dstPort())
                        .addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4));

                                    sLogger.trace("Remove socks5 server encoder");
                                    ctx.pipeline().remove(Socks5ServerEncoder.class);

                                    sLogger.trace("FINAL channels:{}", ctx.pipeline());
                                } else {
                                    if (ctx.channel().isActive()) {
                                        ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4))
                                                .addListener(ChannelFutureListener.CLOSE);
                                    }
                                }
                            }
                        });
            }

            sLogger.trace("Remove command request decoder");
            ctx.pipeline().remove(Socks5CommandRequestDecoder.class);

            sLogger.trace("Remove command request handler");
            ctx.pipeline().remove(this);
        } else {
            sLogger.warn("Unsupported command type:{}", request.type());
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        sLogger.warn("Socks5CommandRequestHandler caught exception\n", cause);
        //ChannelUtil.closeOnFlush(ctx.channel());
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
