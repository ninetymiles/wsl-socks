package com.rex.proxy.socks.v5;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

public final class Socks5UdpRelayHandler extends SimpleChannelInboundHandler<Socks5UdpRelayMessage> {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks5UdpRelayHandler.class);

    private final Bootstrap mBootstrap;
    private final Map<SocketAddress, Channel> mChannelMap = new HashMap<>();

    public Socks5UdpRelayHandler(EventLoop loop) {
        sLogger.trace("UdpRelay init with loop {}", loop);
        mBootstrap = new Bootstrap()
                .group(loop)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) throws Exception {
                        sLogger.trace("UdpRelay init with channel {}", ch);
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                                sLogger.debug("Outbound {} received data:{} from [{}]",
                                        ctx.channel(),
                                        msg.content().readableBytes(),
                                        msg.sender());

                                Channel internal = mChannelMap.get(msg.sender());
                                if (internal != null && internal.isActive()) {
                                    sLogger.debug("Forward data:{} to inbound {}", msg.content().readableBytes(), internal);
                                    // msg default will release after func return
                                    // retain the ref count or need copy the content before send
                                    ReferenceCountUtil.retain(msg);
                                    internal.writeAndFlush(new Socks5UdpRelayMessage.Builder()
                                            .data(msg.content())
                                            .build());
                                }
                            }
                        });
                    }
                });
        mBootstrap.bind(new InetSocketAddress(0));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5UdpRelayMessage msg) throws Exception {
        sLogger.debug("Inbound {} received data:{} dst:[{}:{}]",
                ctx.channel(),
                msg.data.readableBytes(),
                msg.dstAddr,
                msg.dstPort);

        final Channel internal = ctx.channel();
        mBootstrap.connect(msg.dstAddr, msg.dstPort).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Channel external = future.channel();
                sLogger.debug("Outbound {} connected", external);

                sLogger.debug("Inbound {} associate to [{}]", internal, external.remoteAddress());
                mChannelMap.put(external.remoteAddress(), internal);

                sLogger.debug("Forward data:{} to outbound {}", msg.data.readableBytes(), external);
                external.writeAndFlush(msg.data);
            }
        }).sync();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // ctx: [id: 0x0182c0ea, L:/127.0.0.1:1080 - R:/127.0.0.1:54536]
        // cause: java.io.IOException: Connection reset by peer
        sLogger.warn("{} - {}", ctx.channel(), cause.getMessage());
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        sLogger.trace("UdpRelay finalize");
        for (SocketAddress key : mChannelMap.keySet()) {
            Channel ch = mChannelMap.get(key);
            if (ch.isActive()) {
                ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
        mChannelMap.clear();
    }
}
