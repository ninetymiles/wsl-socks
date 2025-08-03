package com.rex.proxy.http;

import com.rex.proxy.WslLocal;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialize the server channel pipeline
 */
public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(HttpServerInitializer.class);

    private final EventLoopGroup mWorkerGroup;
    private final WslLocal.Configuration mConfig;

    public HttpServerInitializer(EventLoopGroup group, WslLocal.Configuration config) {
        sLogger.trace("<init>");
        mWorkerGroup = group;
        mConfig = config;
        sLogger.debug("Config:{}", mConfig);
    }

    @Override // ChannelInitializer
    protected void initChannel(SocketChannel ch) throws Exception {
        sLogger.trace("initChannel");
        //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG));
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                .addLast(new HttpServerPathInterceptor(mWorkerGroup, mConfig));
    }
}
