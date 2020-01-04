package com.rex.wsproxy.websocket;

import com.rex.wsproxy.WsProxyLocal;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
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
 * Initialize the client channel pipeline
 * WsProxyLocal (Socks Server) will use this initializer to handshake with WsProxyServer (WebSocket Server)
 */
public class WsClientInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsClientInitializer.class);

    private static final String WS_SUBPROTOCOL = "com.rex.websocket.protocol.proxy2";

    private final WsProxyLocal.Configuration mConfig;
    private final ChannelHandlerContext mContext; // Socks connection
    private final WsClientHandler.ResponseListener mListener;
    private SslContext mSslContext;
    private String mDstAddress;
    private int mDstPort;

    public WsClientInitializer(final WsProxyLocal.Configuration config, final ChannelHandlerContext ctx, String dstAddr, int dstPort, WsClientHandler.ResponseListener listener) {
        sLogger.trace("<init>");
        mConfig = config;
        mContext = ctx;
        mDstAddress = dstAddr;
        mDstPort = dstPort;
        mListener = listener;

        if ("wss".equalsIgnoreCase(mConfig.proxyUri.getScheme())) {
            try {
                SslContextBuilder builder = SslContextBuilder.forClient();
                if (! mConfig.proxyCertVerify) {
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
        sLogger.trace("initChannel");
        if (mSslContext != null) {
            ch.pipeline().addLast(mSslContext.newHandler(ch.alloc()));
        }
        ch.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                .addLast(new WebSocketClientProtocolHandler(mConfig.proxyUri, WebSocketVersion.V13, WS_SUBPROTOCOL, false, null, 65535))
                .addLast(new WsClientHandler(mContext.channel(), mDstAddress, mDstPort, mConfig.proxyUid, mListener));
    }
}
