package com.rex.websocket;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialize the server channel pipeline
 */
public class WsServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsServerInitializer.class);

    private final SslContext mSslContext;
    private final EventLoopGroup mWorkerGroup;

    public WsServerInitializer(SslContext sslContext, EventLoopGroup group) {
        sLogger.trace("<init>");
        mSslContext = sslContext;
        mWorkerGroup = group;
    }

    @Override // ChannelInitializer
    protected void initChannel(SocketChannel ch) throws Exception {
        sLogger.trace("initChannel");
        if (mSslContext != null) {
            sLogger.debug("Init SSL");
            ch.pipeline().addLast(mSslContext.newHandler(ch.alloc()));
        }
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                .addLast(new WsServerPathInterceptor(mWorkerGroup));

        // XXX: If we need WebSocketFrameAggregator or ContinuationWebSocketFrame ?
    }
}
