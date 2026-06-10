package com.rex.proxy.websocket;

import com.rex.proxy.WslServer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialize the server channel pipeline
 */
public class WsServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsServerInitializer.class);

    private final SslContext mSslContext;
    private final EventLoopGroup mWorkerGroup;
    private final WslServer.Configuration mConfig;

    public WsServerInitializer(EventLoopGroup group, WslServer.Configuration config , SslContext sslContext) {
        sLogger.trace("<init>");
        mWorkerGroup = group;
        mConfig = config;
        mSslContext = sslContext;
    }

    @Override // ChannelInitializer
    protected void initChannel(SocketChannel ch) throws Exception {
        sLogger.trace("initChannel");
        if (mSslContext != null) {
            sLogger.debug("Init SSL");
            ch.pipeline().addLast(mSslContext.newHandler(ch.alloc()));
        }

        // Add idle timeout handler (15 minutes) to close inactive WebSocket connections
        ch.pipeline().addLast(new IdleStateHandler(0, 0, 900) { // Neither read nor write for 15min
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof IdleStateEvent) {
                    sLogger.info("WebSocket connection idle timeout {}, closing", ctx.channel().remoteAddress());
                    ctx.close();
                }
            }
        });

        //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                .addLast(new WsServerPathInterceptor(mWorkerGroup, mConfig));
    }
}
