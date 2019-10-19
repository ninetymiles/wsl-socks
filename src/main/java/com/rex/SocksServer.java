package com.rex;

import com.rex.socks.SocksServerInitializer;
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
import java.util.Properties;

/**
 * Socks server
 */
public class SocksServer {

    private static final Logger sLogger = LoggerFactory.getLogger(SocksServer.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2

    private ChannelFuture mChannelFuture;

    public static class Configuration {
        public String bindAddress;
        public int bindPort;
        public String authUser;
        public String authPassword;
        public Configuration() {
        }
        public Configuration(String addr, int port) {
            bindAddress = addr;
            bindPort = port;
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
    public SocksServer() {
        sLogger.trace("<init>");
    }

    synchronized public SocksServer config(Configuration conf) {
        if (conf.bindAddress != null) mConfig.bindAddress = conf.bindAddress;
        if (conf.bindPort != 0) mConfig.bindPort = conf.bindPort;
        if (conf.authUser != null) mConfig.authUser = conf.authUser;
        if (conf.authPassword != null) mConfig.authPassword = conf.authPassword;
        return this;
    }

    synchronized public SocksServer config(InputStream in) {
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
    synchronized public SocksServer start() {
        if (mChannelFuture != null) {
            sLogger.warn("already started");
            return this;
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
    synchronized public SocksServer stop() {
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
        SocksServer server = new SocksServer();
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
                System.out.println("Usage: WsTunnelClient [options]");
                System.out.println("    -a | --addr     Socket bind address, default 0.0.0.0");
                System.out.println("    -p | --port     Socket bind port, default 5081");
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
