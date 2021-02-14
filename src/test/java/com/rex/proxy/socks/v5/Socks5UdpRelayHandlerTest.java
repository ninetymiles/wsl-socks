package com.rex.proxy.socks.v5;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class Socks5UdpRelayHandlerTest {

    private final Logger mLogger = LoggerFactory.getLogger(Socks5UdpRelayHandlerTest.class.getSimpleName());

    // Client app send udp with socks header
    // Should remove the socks header and forward to target server
    // Remote server send response back to socks server
    // Should forward back to client app with socks header
    @Test
    public void testUdpRelay() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    public void channelRead0(ChannelHandlerContext ctx, DatagramPacket pkt) throws Exception {
                        mLogger.debug("Mock server {} receive pkt:{}", ctx.channel(), pkt);
                        String response = StandardCharsets.UTF_8
                                .newDecoder()
                                .decode(pkt.content().nioBuffer())
                                .toString()
                                .toLowerCase();
                        ctx.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(response.getBytes()), pkt.sender()));
                    }
                });
        Channel ch = bootstrap.bind(0)
                .sync()
                .channel();
        InetSocketAddress addr = (InetSocketAddress) ch.localAddress();
        mLogger.debug("Mock server [{}]", addr);

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new Socks5UdpRelayHandler(ch.eventLoop())); // Need a real event loop instead get from embedded channel
        channel.writeInbound(new Socks5UdpRelayMessage.Builder()
                .dstAddr("127.0.0.1")
                .dstPort(addr.getPort())
                .data(Unpooled.wrappedBuffer("HelloWorld!".getBytes()))
                .build());

        Thread.sleep(100);

        Socks5UdpRelayMessage msg = channel.readOutbound();
        assertEquals("helloworld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(msg.data.nioBuffer())
                .toString());
    }
}
