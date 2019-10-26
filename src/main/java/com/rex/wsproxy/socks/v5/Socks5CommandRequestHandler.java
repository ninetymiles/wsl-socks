package com.rex.wsproxy.socks.v5;

import com.rex.wsproxy.socks.RelayHandler;
import com.rex.wsproxy.socks.SocksUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public final class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks5CommandRequestHandler.class);

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final Socks5CommandRequest request) throws Exception {
        if (Socks5CommandType.CONNECT.equals(request.type())) {
            sLogger.debug("CommandRequest {} {}:{}", request.type(), request.dstAddr(), request.dstPort());
            new Bootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            sLogger.debug("Relay {} with {}", ctx.channel(), ch);
                            //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print relayed data
                            ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                            ctx.pipeline().addLast(new RelayHandler(ch));
                        }
                    })
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
        SocksUtils.closeOnFlush(ctx.channel());
    }
}
