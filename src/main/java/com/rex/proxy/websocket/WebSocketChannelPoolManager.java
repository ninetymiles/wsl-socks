package com.rex.proxy.websocket;

import com.rex.proxy.WslLocal;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebSocket connection pools for different target addresses.
 * Each target address (host:port) has its own connection pool.
 * Connections are reused to avoid the overhead of TLS and WebSocket handshakes.
 */
public class WebSocketChannelPoolManager {

    private static final Logger sLogger = LoggerFactory.getLogger(WebSocketChannelPoolManager.class);

    // Pool configuration
    private static final int MAX_PENDING_ACQUIRES = 100;
    private static final long ACQUIRE_TIMEOUT_MILLIS = 10000; // 10 seconds
    private static final long IDLE_TIMEOUT_SECONDS = 900; // 15 minutes

    // Singleton instance
    private static volatile WebSocketChannelPoolManager sInstance;

    // Map from "host:port" to connection pool
    private final ConcurrentHashMap<String, SimpleChannelPool> mPoolMap = new ConcurrentHashMap<>();

    private WebSocketChannelPoolManager() {
        sLogger.trace("<init>");
    }

    /**
     * Get singleton instance
     */
    public static WebSocketChannelPoolManager getInstance() {
        if (sInstance == null) {
            synchronized (WebSocketChannelPoolManager.class) {
                if (sInstance == null) {
                    sInstance = new WebSocketChannelPoolManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * Acquire a WebSocket channel from the pool.
     * If no idle connection exists, a new one will be created.
     *
     * @param dstAddr Destination address for logging
     * @param dstPort Destination port for logging
     * @param localCtx The local socket context (SOCKS or HTTP)
     * @param config WslLocal configuration
     * @return Future that will be notified when a channel is acquired
     */
    public Future<Channel> acquire(String dstAddr, int dstPort, ChannelHandlerContext localCtx, WslLocal.Configuration config) {
        String poolKey = getPoolKey(dstAddr, dstPort);
        sLogger.debug("Acquire channel for {} from pool {}", dstAddr + ":" + dstPort, poolKey);

        SimpleChannelPool pool = mPoolMap.computeIfAbsent(poolKey, key -> createPool(config, localCtx));
        return pool.acquire();
    }

    /**
     * Release a WebSocket channel back to the pool.
     * The channel will be cleaned up and kept alive for reuse.
     *
     * @param channel The WebSocket channel to release
     * @param dstAddr Destination address for pool lookup
     * @param dstPort Destination port for pool lookup
     */
    public void release(Channel channel, String dstAddr, int dstPort) {
        String poolKey = getPoolKey(dstAddr, dstPort);
        SimpleChannelPool pool = mPoolMap.get(poolKey);

        if (pool != null && channel != null && channel.isActive()) {
            sLogger.debug("Release channel {} to pool {}", channel, poolKey);

            // Clean up business handlers before returning to pool
            cleanupChannel(channel);

            pool.release(channel);
        } else {
            sLogger.warn("No pool found for {} or channel inactive, closing channel", poolKey);
            if (channel != null && channel.isActive()) {
                channel.close();
            }
        }
    }

    /**
     * Create a new connection pool for a specific target address
     */
    private SimpleChannelPool createPool(WslLocal.Configuration config, ChannelHandlerContext ctx) {
        sLogger.info("Create new connection pool");

        // Determine actual connection address
        String wsHost = config.proxyUri.getHost();
        int wsPort = config.proxyUri.getPort();
        if (wsPort == -1) {
            if ("wss".equalsIgnoreCase(config.proxyUri.getScheme())) {
                wsPort = 443;
            } else {
                wsPort = 80;
            }
        }

        InetSocketAddress serverAddress = InetSocketAddress.createUnresolved(wsHost, wsPort);
        EventLoop eventLoop = ctx.channel().eventLoop();

        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .remoteAddress(serverAddress);

        // Pool handler - manages channel initialization and cleanup
        AbstractChannelPoolHandler poolHandler = new AbstractChannelPoolHandler() {
            @Override
            public void channelCreated(Channel ch) throws Exception {
                sLogger.debug("Create new WebSocket channel {}", ch);

                // Initialize the WebSocket connection using pooled initializer
                PooledWsClientInitializer initializer = new PooledWsClientInitializer(config);
                initializer.initChannel((io.netty.channel.socket.SocketChannel) ch);

                // Add idle timeout handler (15 minutes)
                ch.pipeline().addFirst("idleHandler", new IdleWebSocketHandler(IDLE_TIMEOUT_SECONDS));
            }

            @Override
            public void channelAcquired(Channel ch) throws Exception {
                sLogger.debug("Acquired channel {} from pool", ch);
            }

            @Override
            public void channelReleased(Channel ch) throws Exception {
                sLogger.debug("Released channel {} to pool", ch);
            }
        };

        // Health checker - verifies channel is still usable
        ChannelHealthChecker healthChecker = channel -> {
            boolean healthy = channel.isActive();
            sLogger.trace("Health check for channel {}: {}", channel, healthy);
            return channel.eventLoop().newSucceededFuture(healthy);
        };

        return new SimpleChannelPool(bootstrap, poolHandler, healthChecker);
    }

    /**
     * Clean up channel before returning to pool.
     * Remove business-specific handlers but keep protocol handlers.
     */
    private void cleanupChannel(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();

        // Remove business data forwarding handlers
        if (pipeline.get(WsProxyWsToRaw.class) != null) {
            sLogger.trace("Remove WsProxyWsToRaw from pipeline");
            pipeline.remove(WsProxyWsToRaw.class);
        }

        if (pipeline.get(WsProxyRawToWs.class) != null) {
            sLogger.trace("Remove WsProxyRawToWs from pipeline");
            pipeline.remove(WsProxyRawToWs.class);
        }

        // Remove WsClientHandler if present
        if (pipeline.get("wsClientHandler") != null) {
            sLogger.trace("Remove WsClientHandler from pipeline");
            pipeline.remove("wsClientHandler");
        }

        // Remove state handler if present
        if (pipeline.get("stateHandler") != null) {
            sLogger.trace("Remove stateHandler from pipeline");
            pipeline.remove("stateHandler");
        }

        // Remove idle timeout handler (added in channelCreated, must be removed on release)
        if (pipeline.get(IdleWebSocketHandler.class) != null) {
            sLogger.trace("Remove IdleWebSocketHandler from pipeline");
            pipeline.remove(IdleWebSocketHandler.class);
        }

        sLogger.trace("Channel cleaned, pipeline: {}", pipeline.names());
    }

    /**
     * Generate pool key from destination address
     */
    private String getPoolKey(String host, int port) {
        return host + ":" + port;
    }

    /**
     * Close all pools and release resources.
     * Should be called on shutdown.
     */
    public void shutdown() {
        sLogger.info("Shutting down all connection pools");
        for (SimpleChannelPool pool : mPoolMap.values()) {
            pool.close();
        }
        mPoolMap.clear();
    }
}
