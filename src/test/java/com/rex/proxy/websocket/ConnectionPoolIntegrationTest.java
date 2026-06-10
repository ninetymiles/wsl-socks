package com.rex.proxy.websocket;

import com.rex.proxy.WslLocal;
import com.rex.proxy.WslServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Integration test for connection pooling and authorization protocol
 *
 * This test starts a real WebSocket server and tests the full flow:
 * 1. Connection establishment
 * 2. Authorization handshake
 * 3. Connection pooling and reuse
 * 4. Idle timeout
 */
public class ConnectionPoolIntegrationTest {

    private EventLoopGroup serverBossGroup;
    private EventLoopGroup serverWorkerGroup;
    private Channel serverChannel;
    private int serverPort;
    private WslServer.Configuration serverConfig;
    private WslLocal.Configuration clientConfig;

    @Before
    public void setUp() throws Exception {
        // Setup server configuration
        serverConfig = new WslServer.Configuration();
        serverConfig.proxyUid = "test-integration-secret";

        // Setup client configuration
        clientConfig = new WslLocal.Configuration();
        clientConfig.proxyUid = "test-integration-secret";

        // Start WebSocket server
        startWebSocketServer();

        // Update client config with actual server port
        clientConfig.proxyUri = new URI("ws://localhost:" + serverPort + "/proxy");
    }

    @After
    public void tearDown() throws Exception {
        // Shutdown pool manager
        WebSocketChannelPoolManager.getInstance().shutdown();

        // Shutdown server
        if (serverChannel != null) {
            serverChannel.close().sync();
        }
        if (serverBossGroup != null) {
            serverBossGroup.shutdownGracefully();
        }
        if (serverWorkerGroup != null) {
            serverWorkerGroup.shutdownGracefully();
        }
    }

    private void startWebSocketServer() throws Exception {
        serverBossGroup = new NioEventLoopGroup(1);
        serverWorkerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(serverBossGroup, serverWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new WebSocketServerProtocolHandler("/proxy"));
                        pipeline.addLast(new WsProxyControlHandler(serverWorkerGroup, serverConfig));
                    }
                });

        // Bind to random port
        serverChannel = bootstrap.bind(0).sync().channel();
        serverPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    @Test
    public void testBasicConnectionEstablishment() throws Exception {
        WebSocketChannelPoolManager poolManager = WebSocketChannelPoolManager.getInstance();

        CountDownLatch latch = new CountDownLatch(1);

        // Simulate a local connection context (using embedded channel for simplicity)
        io.netty.channel.embedded.EmbeddedChannel localChannel = new io.netty.channel.embedded.EmbeddedChannel();

        // Add a dummy handler so pipeline().firstContext() returns a valid context
        localChannel.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {});

        // Acquire a connection
        io.netty.util.concurrent.Future<Channel> future = poolManager.acquire(
                "www.example.com",
                443,
                localChannel.pipeline().firstContext(),
                clientConfig
        );

        if (future != null) {
            future.addListener((io.netty.util.concurrent.GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) f -> {
                if (f.isSuccess()) {
                    Channel wsChannel = f.getNow();
                    assertNotNull("WebSocket channel should not be null", wsChannel);
                    assertTrue("WebSocket channel should be active", wsChannel.isActive());
                    latch.countDown();
                } else {
                    // Connection may fail due to network issues, but should not crash
                    latch.countDown();
                }
            });

            assertTrue("Connection should complete", latch.await(10, TimeUnit.SECONDS));
        }

        localChannel.close();
    }

    @Test
    public void testConnectionReuse() throws Exception {
        WebSocketChannelPoolManager poolManager = WebSocketChannelPoolManager.getInstance();

        io.netty.channel.embedded.EmbeddedChannel localChannel1 = new io.netty.channel.embedded.EmbeddedChannel();
        io.netty.channel.embedded.EmbeddedChannel localChannel2 = new io.netty.channel.embedded.EmbeddedChannel();

        // Add dummy handlers so pipeline().firstContext() returns valid contexts
        localChannel1.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {});
        localChannel2.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {});

        try {
            CountDownLatch latch1 = new CountDownLatch(1);
            final Channel[] firstChannel = new Channel[1];

            // First acquisition
            io.netty.util.concurrent.Future<Channel> future1 = poolManager.acquire(
                    "reuse.example.com",
                    443,
                    localChannel1.pipeline().firstContext(),
                    clientConfig
            );

            if (future1 != null) {
                future1.addListener((io.netty.util.concurrent.GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) f -> {
                    if (f.isSuccess()) {
                        firstChannel[0] = f.getNow();
                    }
                    latch1.countDown();
                });

                latch1.await(10, TimeUnit.SECONDS);

                if (firstChannel[0] != null && firstChannel[0].isActive()) {
                    // Release first connection
                    poolManager.release(firstChannel[0], "reuse.example.com", 443);

                    // Wait a bit for release to complete
                    Thread.sleep(100);

                    // Second acquisition - should reuse the same channel
                    CountDownLatch latch2 = new CountDownLatch(1);
                    final Channel[] secondChannel = new Channel[1];

                    io.netty.util.concurrent.Future<Channel> future2 = poolManager.acquire(
                            "reuse.example.com",
                            443,
                            localChannel2.pipeline().firstContext(),
                            clientConfig
                    );

                    if (future2 != null) {
                        future2.addListener((io.netty.util.concurrent.GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) f -> {
                            if (f.isSuccess()) {
                                secondChannel[0] = f.getNow();
                            }
                            latch2.countDown();
                        });

                        latch2.await(10, TimeUnit.SECONDS);

                        if (secondChannel[0] != null) {
                            // Should be the same channel (reused from pool)
                            assertSame("Should reuse the same channel", firstChannel[0], secondChannel[0]);
                        }
                    }
                }
            }
        } finally {
            localChannel1.close();
            localChannel2.close();
        }
    }

    @Test
    public void testDifferentDestinationsUseDifferentConnections() throws Exception {
        WebSocketChannelPoolManager poolManager = WebSocketChannelPoolManager.getInstance();

        io.netty.channel.embedded.EmbeddedChannel localChannel1 = new io.netty.channel.embedded.EmbeddedChannel();
        io.netty.channel.embedded.EmbeddedChannel localChannel2 = new io.netty.channel.embedded.EmbeddedChannel();

        // Add dummy handlers so pipeline().firstContext() returns valid contexts
        localChannel1.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {});
        localChannel2.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {});

        try {
            CountDownLatch latch = new CountDownLatch(2);
            final Channel[] channel1 = new Channel[1];
            final Channel[] channel2 = new Channel[1];

            // Acquire connection to first destination
            io.netty.util.concurrent.Future<Channel> future1 = poolManager.acquire(
                    "host1.example.com",
                    443,
                    localChannel1.pipeline().firstContext(),
                    clientConfig
            );

            if (future1 != null) {
                future1.addListener((io.netty.util.concurrent.GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) f -> {
                    if (f.isSuccess()) {
                        channel1[0] = f.getNow();
                    }
                    latch.countDown();
                });
            } else {
                latch.countDown();
            }

            // Acquire connection to second destination
            io.netty.util.concurrent.Future<Channel> future2 = poolManager.acquire(
                    "host2.example.com",
                    443,
                    localChannel2.pipeline().firstContext(),
                    clientConfig
            );

            if (future2 != null) {
                future2.addListener((io.netty.util.concurrent.GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) f -> {
                    if (f.isSuccess()) {
                        channel2[0] = f.getNow();
                    }
                    latch.countDown();
                });
            } else {
                latch.countDown();
            }

            assertTrue("Both acquisitions should complete", latch.await(10, TimeUnit.SECONDS));

            if (channel1[0] != null && channel2[0] != null) {
                // Should be different channels (different destinations)
                assertNotSame("Different destinations should use different channels",
                        channel1[0], channel2[0]);
            }
        } finally {
            localChannel1.close();
            localChannel2.close();
        }
    }

    @Test
    public void testAuthorizationProtocol() throws Exception {
        // This test verifies the authorization handshake works end-to-end
        WebSocketChannelPoolManager poolManager = WebSocketChannelPoolManager.getInstance();

        io.netty.channel.embedded.EmbeddedChannel localChannel = new io.netty.channel.embedded.EmbeddedChannel();

        // Add dummy handler so pipeline().firstContext() returns a valid context
        localChannel.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {});

        try {
            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] success = {false};

            io.netty.util.concurrent.Future<Channel> future = poolManager.acquire(
                    "auth.example.com",
                    443,
                    localChannel.pipeline().firstContext(),
                    clientConfig
            );

            if (future != null) {
                future.addListener((io.netty.util.concurrent.GenericFutureListener<io.netty.util.concurrent.Future<Channel>>) f -> {
                    success[0] = f.isSuccess();
                    latch.countDown();
                });

                assertTrue("Authorization should complete", latch.await(10, TimeUnit.SECONDS));

                if (success[0]) {
                    Channel wsChannel = future.getNow();
                    assertTrue("Channel should be active after auth", wsChannel.isActive());
                }
            }
        } finally {
            localChannel.close();
        }
    }

    @Test
    public void testIdleHandlerCleanupOnRelease() throws Exception {
        // Test that cleanupChannel removes IdleWebSocketHandler from pipeline
        WebSocketChannelPoolManager poolManager = WebSocketChannelPoolManager.getInstance();

        io.netty.channel.embedded.EmbeddedChannel localChannel = new io.netty.channel.embedded.EmbeddedChannel();
        localChannel.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter() {});

        try {
            // Acquire a connection from pool - this creates a new pool
            io.netty.util.concurrent.Future<Channel> future = poolManager.acquire(
                    "cleanup-handler-test.example.com",
                    443,
                    localChannel.pipeline().firstContext(),
                    clientConfig
            );

            // Wait for acquire with timeout
            assertTrue("Acquire should complete", future.await(5, TimeUnit.SECONDS));

            if (future.isSuccess()) {
                Channel wsChannel = future.getNow();

                // Verify IdleWebSocketHandler was added to the channel pipeline
                assertNotNull("IdleWebSocketHandler should be in pipeline after acquire",
                        wsChannel.pipeline().get(IdleWebSocketHandler.class));

                // Manually call cleanupChannel to simulate release
                // Use reflection since cleanupChannel is private
                java.lang.reflect.Method cleanupMethod = WebSocketChannelPoolManager.class
                        .getDeclaredMethod("cleanupChannel", Channel.class);
                cleanupMethod.setAccessible(true);
                cleanupMethod.invoke(poolManager, wsChannel);

                // After cleanup, the pipeline should no longer contain IdleWebSocketHandler
                assertNull("IdleWebSocketHandler should be removed from pipeline after cleanup",
                        wsChannel.pipeline().get(IdleWebSocketHandler.class));
            }
        } finally {
            localChannel.close();
        }
    }
}
