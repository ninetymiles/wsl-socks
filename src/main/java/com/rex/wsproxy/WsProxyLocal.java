package com.rex.wsproxy;

import com.rex.wsproxy.socks.SocksServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Properties;

/**
 * Socks server
 */
public class WsProxyLocal {

    private static final Logger sLogger = LoggerFactory.getLogger(WsProxyLocal.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2

    private ChannelFuture mChannelFuture;

    public static class Configuration {
        public String bindAddress;
        public int bindPort;
        public String authUser;
        public String authPassword;
        public URI proxyUri;
        public String proxySubProtocol;
        public Boolean proxyCertVerify; // Only works for WSS scheme
        public Configuration() {
        }
        public Configuration(String addr, int port) {
            bindAddress = addr;
            bindPort = port;
        }
        public Configuration(String addr, int port, URI proxyUri, String proxySubProtocol) {
            this(addr, port);
            proxyUri = proxyUri;
            proxySubProtocol = proxySubProtocol;
            proxyCertVerify = Boolean.FALSE;
        }
        public Configuration(String addr, int port, String user, String password) {
            this(addr, port);
            authUser = user;
            authPassword = password;
        }
    }
    private Configuration mConfig = new Configuration("0.0.0.0", 1080);

    /**
     * Construct the server
     */
    public WsProxyLocal() {
        sLogger.trace("<init>");
    }

    synchronized public WsProxyLocal config(Configuration conf) {
        if (conf.bindAddress != null) mConfig.bindAddress = conf.bindAddress;
        if (conf.bindPort != 0) mConfig.bindPort = conf.bindPort;
        if (conf.authUser != null) mConfig.authUser = conf.authUser;
        if (conf.authPassword != null) mConfig.authPassword = conf.authPassword;
        if (conf.proxyUri != null) mConfig.proxyUri = conf.proxyUri;
        if (conf.proxySubProtocol != null) mConfig.proxySubProtocol = conf.proxySubProtocol;
        if (conf.proxyCertVerify != null) mConfig.proxyCertVerify = conf.proxyCertVerify;
        return this;
    }

    synchronized public WsProxyLocal config(InputStream in) {
        try {
            Properties config = new Properties();
            config.load(in);
            for (String name : config.stringPropertyNames()) {
                switch (name) {
                case "bindAddress":
                    mConfig.bindAddress = config.getProperty(name);
                    break;
                case "bindPort":
                    mConfig.bindPort = Integer.parseInt(config.getProperty(name));
                    break;
                case "authUser":
                    mConfig.authUser = config.getProperty(name);
                    break;
                case "authPassword":
                    mConfig.authPassword = config.getProperty(name);
                    break;
                case "proxyUri":
                    mConfig.proxyUri = URI.create(config.getProperty(name));
                    break;
                case "proxySubProtocol":
                    mConfig.proxySubProtocol = config.getProperty(name);
                    break;
                case "proxyCertVerify":
                    mConfig.proxyCertVerify = Boolean.parseBoolean(config.getProperty(name));
                    break;
                }
            }
        } catch (IOException ex) {
            sLogger.warn("Failed to load config\n", ex);
        }
        return this;
    }

    /**
     * Start the socks5 server
     */
    synchronized public WsProxyLocal start() {
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

        SocketAddress address = new InetSocketAddress(mConfig.bindAddress, mConfig.bindPort);
        sLogger.info("start address:{}", address);

        mChannelFuture = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new SocksServerInitializer(mConfig))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(address)
                .syncUninterruptibly();
        return this;
    }

    /**
     * Stop the socks5 server
     */
    synchronized public WsProxyLocal stop() {
        sLogger.info("stop");
        if (mChannelFuture == null) {
            sLogger.warn("not started");
            return this;
        }
        mChannelFuture.channel().close();
        mChannelFuture.channel().closeFuture().syncUninterruptibly();
        mChannelFuture = null;
        return this;
    }

    public int port() {
        return mConfig.bindPort;
    }

    public static void main(String[] args) {
        WsProxyLocal server = new WsProxyLocal();
        Configuration config = new Configuration();
        int idx = 0;
        while (idx < args.length) {
            String key = args[idx++];
            if ("-a".equals(key) || "--addr".equals(key)) {
                config.bindAddress = args[idx++];
            }
            if ("-p".equals(key) || "--port".equals(key)) {
                try {
                    config.bindPort = Integer.parseInt(args[idx++]);
                } catch (NumberFormatException ex) {
                    sLogger.warn("Failed to parse port\n", ex);
                }
            }
            if ("-c".equals(key) || "--config".equals(key)) {
                String configFileName = args[idx++];
                try {
                    server.config(new FileInputStream(configFileName));
                } catch (FileNotFoundException ex) {
                    sLogger.warn("Failed to load config file " + configFileName + "\n", ex);
                }
            }
            if ("-h".equals(key) || "--help".equals(key)) {
                System.out.println("Usage: WsProxyLocal [options]");
                System.out.println("    -a | --addr     Socket bind address, default 0.0.0.0");
                System.out.println("    -p | --port     Socket bind port, default 1080");
                System.out.println("    -c | --config   Configuration file");
                System.out.println("    -h | --help     Help page");
                return;
            }
        }
        try {
            server.config(config);
            server.start();
        } catch (Throwable tr) {
            sLogger.error("Failed to start server\n", tr);
        }
    }
}
