package com.rex.wsproxy.socks.v4;

import com.rex.wsproxy.WsProxyLocal;
import com.rex.wsproxy.socks.SocksProxyInitializer;
import com.rex.wsproxy.websocket.WsClientHandler;
import com.rex.wsproxy.websocket.WsClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v4.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class Socks4CommandRequestHandler extends SimpleChannelInboundHandler<Socks4CommandRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks4CommandRequestHandler.class);
    private WsProxyLocal.Configuration mConfig;

    public Socks4CommandRequestHandler(WsProxyLocal.Configuration config) {
        sLogger.trace("<init>");
        mConfig = config;
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Socks4CommandRequest request) throws Exception {
        if (Socks4CommandType.CONNECT.equals(request.type())) {
            sLogger.debug("CommandRequest {} {}:{}", request.type(), request.dstAddr(), request.dstPort());
            Bootstrap bootstrap = new Bootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                    .option(ChannelOption.TCP_NODELAY, true)
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
                        if (! ctx.channel().isActive()) {
                            return;
                        }
                        if (success) {
                            ctx.writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS));

                            sLogger.trace("Remove socks4 server encoder");
                            ctx.pipeline().remove(Socks4ServerEncoder.class);
                        } else {
                            ctx.writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED))
                                    .addListener(ChannelFutureListener.CLOSE);
                        }
                    }
                };
                bootstrap.handler(new WsClientInitializer(mConfig, ctx, request.dstAddr(), request.dstPort(), responseListener))
                        .connect(dstAddr, dstPort);
            } else {
                sLogger.debug("Proxy direct to {}:{}", request.dstAddr(), request.dstPort());
                bootstrap.handler(new SocksProxyInitializer(ctx))
                        .connect(request.dstAddr(), request.dstPort())
                        .addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (! ctx.channel().isActive()) {
                                    return;
                                }
                                if (future.isSuccess()) {
                                    ctx.channel().writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS));

                                    sLogger.trace("Remove socks4 server encoder");
                                    ctx.pipeline().remove(Socks4ServerEncoder.class);

                                    sLogger.trace("FINAL channels:{}", ctx.pipeline());
                                } else {
                                    ctx.channel().writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED))
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                            }
                        });
            }

            sLogger.trace("Remove socks4 server decoder");
            ctx.pipeline().remove(Socks4ServerDecoder.class);

            sLogger.trace("Remove command request handler");
            ctx.pipeline().remove(this);
        } else {
            sLogger.warn("Unsupported command type:{}", request.type());
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        sLogger.warn("{} - {}", ctx.channel(), cause.getMessage());
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
