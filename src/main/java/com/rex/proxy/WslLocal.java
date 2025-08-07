package com.rex.proxy;

import com.rex.proxy.http.HttpServerInitializer;
import com.rex.proxy.socks.SocksServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Socks server
 */
public class WslLocal {

    private static final Logger sLogger = LoggerFactory.getLogger(WslLocal.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2

    private ChannelFuture mChannelFuture;
    private ChannelFuture mHttpChannelFuture;

    // Used for vpn support, protect form loop route to tun interface
    public interface SocketCallback {
        void onConnect(Socket socket);
    }

    public static class Configuration {
        public String bindAddress;
        public Integer bindPort;
        public String bindProtocol;
        public String authUser;
        public String authPassword;
        public URI proxyUri;
        public String proxyUid;
        public Boolean proxyCertVerify; // Only works for WSS scheme
        public SocketCallback callback;
        public Configuration() {
        }
        public Configuration(int port) {
            bindPort = port;
        }
        public Configuration(String addr, int port) {
            this(port);
            bindAddress = addr;
        }
        public Configuration(String addr, int port, URI uri, String uid) {
            this(addr, port);
            proxyUri = uri;
            proxyUid = uid;
            proxyCertVerify = Boolean.FALSE;
        }
        public Configuration(String addr, int port, String user, String password) {
            this(addr, port);
            authUser = user;
            authPassword = password;
        }
    }
    private final Configuration mConfig = new Configuration("0.0.0.0", 1080);

    /**
     * Construct the server
     */
    public WslLocal() {
        sLogger.trace("<init>");
    }

    synchronized public WslLocal config(Configuration conf) {
        if (conf.bindAddress != null) mConfig.bindAddress = conf.bindAddress;
        if (conf.bindPort != null) mConfig.bindPort = conf.bindPort;
        if (conf.bindProtocol != null) mConfig.bindProtocol = conf.bindProtocol;
        if (conf.authUser != null) mConfig.authUser = conf.authUser;
        if (conf.authPassword != null) mConfig.authPassword = conf.authPassword;
        if (conf.proxyUri != null) mConfig.proxyUri = conf.proxyUri;
        if (conf.proxyUid != null) mConfig.proxyUid = conf.proxyUid;
        if (conf.proxyCertVerify != null) mConfig.proxyCertVerify = conf.proxyCertVerify;
        if (conf.callback != null) mConfig.callback = conf.callback;
        return this;
    }

    /**
     * Start the socks5 server
     */
    synchronized public WslLocal start() {
        if (mChannelFuture != null) {
            sLogger.warn("already started");
            return this;
        }

        if (mConfig.proxyUri != null) {
            String scheme = mConfig.proxyUri.getScheme() == null ? "ws" : mConfig.proxyUri.getScheme();
            final String host = mConfig.proxyUri.getHost() == null ? "127.0.0.1" : mConfig.proxyUri.getHost();
            final int port;
            if (mConfig.proxyUri.getPort() == -1) {
                if ("ws".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("wss".equalsIgnoreCase(scheme)) {
                    port = 443;
                } else {
                    port = -1;
                }
            } else {
                port = mConfig.proxyUri.getPort();
            }
            if (port == -1) {
                sLogger.error("Unknown port");
                return this;
            }
            if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
                sLogger.error("Only WS(S) is supported.");
                return this;
            }
            sLogger.trace("scheme:{} host:{} port:{}", scheme, host, port);
        }

        ChannelInitializer<SocketChannel> childHandler;
        if ("http".equalsIgnoreCase(mConfig.bindProtocol)) {
            sLogger.info("Bind HTTP proxy");
            childHandler = new HttpServerInitializer(mConfig);
        } else {
            sLogger.info("Bind SOCKS proxy");
            childHandler = new SocksServerInitializer(mConfig);
        }
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(childHandler)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        mChannelFuture = bootstrap
                .bind(new InetSocketAddress(mConfig.bindAddress, mConfig.bindPort))
                .syncUninterruptibly();
        sLogger.info("Bind address:{}", mChannelFuture.channel().localAddress());

        return this;
    }

    /**
     * Stop the socks5 server
     */
    synchronized public WslLocal stop() {
        sLogger.info("stop");
        if (mChannelFuture == null) {
            sLogger.warn("not started");
            return this;
        }
        mChannelFuture.channel()
                .close()
                .syncUninterruptibly();
        mChannelFuture = null;
        return this;
    }

    public int port() {
        try {
            return ((InetSocketAddress) mChannelFuture.channel().localAddress()).getPort();
        } catch (Exception ex) {
            sLogger.warn("Failed to get port");
        }
        return mConfig.bindPort;
    }

    public enum RemoteStateEvent {
        REMOTE_READY,
        REMOTE_FAILED
    }
}
