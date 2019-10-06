package com.rex.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import jdk.nashorn.internal.codegen.CompilerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket server
 * TODO: Support TLS
 */
public class WsServer {

    private static final Logger sLogger = LoggerFactory.getLogger(WsServer.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2

    private final ServerBootstrap mBootstrap;
    private ChannelFuture mChannelFuture;

    private final List<WsTunnelConnection> mConnectionList = new ArrayList<>();

    public interface Callback {
        void onAdded(WsTunnelConnection conn);
        void onReceived(WsTunnelConnection conn, ByteBuffer data);
        void onRemoved(WsTunnelConnection conn);
    }
    private Callback mCallback;

    /**
     * Construct the server
     */
    public WsServer() {
        sLogger.trace("<init>");

        mBootstrap = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override // ChannelInitializer
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                                .addLast(new WsPathInterceptor(mConnCallback));
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true);
    }

    /**
     * Start the websocket server
     *
     * @param address
     */
    synchronized public WsServer start(final SocketAddress address) {
        sLogger.trace("start address:{}", address);
        if (mChannelFuture != null) {
            sLogger.warn("already started");
            return this;
        }
        mChannelFuture = mBootstrap.bind(address)
                .syncUninterruptibly();
        return this;
    }

    /**
     * Stop the websocket server
     */
    synchronized public WsServer stop() {
        sLogger.trace("stop");
        if (mChannelFuture == null) {
            sLogger.warn("not started");
            return this;
        }
        mChannelFuture.channel().close();
        mChannelFuture.channel().closeFuture().syncUninterruptibly();
        mChannelFuture = null;

        synchronized (mConnectionList) {
            for (WsTunnelConnection conn : mConnectionList) {
                conn.close();
            }
        }
        return this;
    }

    public WsServer setCallback(Callback cb) {
        mCallback = cb;
        return this;
    }

    private WsTunnelConnection.Callback mConnCallback = new WsTunnelConnection.Callback() {
        @Override
        public void onConnected(WsTunnelConnection conn) {
            sLogger.trace("connection {} connect", conn);
            synchronized (mConnectionList) {
                mConnectionList.add(conn);
            }
            if (mCallback != null) {
                mCallback.onAdded(conn);
            }
        }
        @Override
        public void onReceived(WsTunnelConnection conn, ByteBuffer data) {
            sLogger.trace("connection {} receive {}", conn, data.remaining());
            if (mCallback != null) {
                mCallback.onReceived(conn, data);
            }
        }
        @Override
        public void onDisconnected(WsTunnelConnection conn) {
            sLogger.trace("connection {} disconnect", conn);
            synchronized (mConnectionList) {
                mConnectionList.remove(conn);
            }
            if (mCallback != null) {
                mCallback.onRemoved(conn);
            }
        }
    };
}
