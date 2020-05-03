package com.rex.proxy.socks;

import com.rex.proxy.WslLocal;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetSocketAddress;

// When client send CommandRequest BIND
// Socks server should bind a temp socket
public final class SocksBindInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(SocksBindInitializer.class);

    private final WslLocal.Configuration mConfig;
    private final ChannelHandlerContext mContext; // Socks client

    public SocksBindInitializer(final WslLocal.Configuration config, final ChannelHandlerContext ctx) {
        mConfig  = config;
        mContext = ctx;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Will pint all the traffic
        ch.pipeline()
                .addLast(new IdleStateHandler(0, 0, 900) { // Neither read nor write for 15min
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        sLogger.debug("Idle connection {}", ctx.channel().remoteAddress());
                        if (evt instanceof IdleStateEvent) {
                            ctx.close();
                        }
                    }
                })
                .addLast(new SocksProxyInitializer(mConfig, mContext));

        InetSocketAddress sockAddr = ch.remoteAddress();
        Socks5AddressType type = Socks5AddressType.IPv4;
        if (sockAddr.getAddress() instanceof Inet6Address) {
            type = Socks5AddressType.IPv6;
        }
        sLogger.debug("Accept address:{}", sockAddr);
        mContext.channel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, type, sockAddr.getAddress().getHostAddress(), sockAddr.getPort()));
    }
}
