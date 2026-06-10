package com.rex.proxy.websocket;

import com.rex.proxy.WslLocal;
import com.rex.proxy.websocket.control.WsProxyControlCodec;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleUserEventChannelHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;

/**
 * Initialize WebSocket client channel for connection pooling.
 * This initializer only sets up the protocol stack (SSL, HTTP, WebSocket)
 * and does NOT set up business handlers or closeFuture listeners.
 * Business handlers will be added/removed dynamically when acquiring/releasing from pool.
 */
public class PooledWsClientInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(PooledWsClientInitializer.class);

    private static final String WS_SUBPROTOCOL = "com.rex.websocket.protocol.proxy2";

    private final WslLocal.Configuration mConfig;
    private SslContext mSslContext;

    public PooledWsClientInitializer(WslLocal.Configuration config) {
        sLogger.trace("<init>");
        mConfig = config;

        if ("wss".equalsIgnoreCase(mConfig.proxyUri.getScheme())) {
            try {
                SslContextBuilder builder = SslContextBuilder.forClient();
                if (!mConfig.proxyCertVerify) {
                    builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                }
                mSslContext = builder.build();
            } catch (SSLException ex) {
                sLogger.warn("Failed to init ssl\n", ex);
            }
        }
    }

    @Override // ChannelInitializer
    protected void initChannel(SocketChannel ch) throws Exception {
        sLogger.trace("Initialize pooled WebSocket channel: {}", ch);

        if (mSslContext != null) {
            // TLS-SNI: https://www.cloudflare.com/learning/ssl/what-is-sni/
            ch.pipeline().addLast(mSslContext.newHandler(ch.alloc(), mConfig.proxyUri.getHost(), mConfig.proxyUri.getPort()));
        }

        ch.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                .addLast(new WebSocketClientProtocolHandler(mConfig.proxyUri, WebSocketVersion.V13, WS_SUBPROTOCOL, false, null, 65535))
                .addLast(new HandshakeCompleteHandler());

        sLogger.trace("Pooled channel pipeline initialized: {}", ch.pipeline().names());
    }

    /**
     * Handler to process WebSocket handshake completion for pooled connections
     */
    @ChannelHandler.Sharable
    private static class HandshakeCompleteHandler extends SimpleUserEventChannelHandler<WebSocketClientProtocolHandler.ClientHandshakeStateEvent> {

        private static final Logger sLogger = LoggerFactory.getLogger(HandshakeCompleteHandler.class);

        @Override
        protected void eventReceived(ChannelHandlerContext ctx, WebSocketClientProtocolHandler.ClientHandshakeStateEvent evt) throws Exception {
            sLogger.info("Pooled channel handshake event: {} for channel: {}", evt, ctx.channel());

            if (WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE.equals(evt)) {
                // Handshake complete - add control codec for message conversion
                ctx.pipeline()
                        .addLast(new WsProxyControlCodec())
                        .remove(this);
                sLogger.info("Pooled WebSocket handshake completed, codec added: {}", ctx.channel());
            }
        }
    }
}
