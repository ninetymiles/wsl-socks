package com.rex.wsproxy;

import com.rex.wsproxy.websocket.WsServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 * WebSocket proxy server
 */
public class WsProxyServer {

    private static final Logger sLogger = LoggerFactory.getLogger(WsProxyServer.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2

    private ChannelFuture mChannelFuture;

    public static class Configuration {
        public String bindAddress;
        public int bindPort;
        public String sslCert;
        public String sslKey; // In PKCS8 format
        public String sslKeyPassword; // Leave it null if key not encrypted
        public Configuration() {
        }
        public Configuration(String addr, int port) {
            bindAddress = addr;
            bindPort = port;
        }
        public Configuration(String addr, int port, String cert, String key) {
            this(addr, port);
            sslCert = cert;
            sslKey = key;
        }
        public Configuration(String addr, int port, String cert, String key, String keyPassword) {
            this(addr, port, cert, key);
            sslKeyPassword = keyPassword;
        }
    }
    private Configuration mConfig = new Configuration("0.0.0.0", 9777); // WSSP in T9 keyboard

    /**
     * Construct the server
     */
    public WsProxyServer() {
        sLogger.trace("<init>");
    }

    synchronized public WsProxyServer config(Configuration conf) {
        if (conf.bindAddress != null) mConfig.bindAddress = conf.bindAddress;
        if (conf.bindPort != 0) mConfig.bindPort = conf.bindPort;
        if (conf.sslCert != null) mConfig.sslCert = conf.sslCert;
        if (conf.sslKey != null) mConfig.sslKey = conf.sslKey;
        if (conf.sslKeyPassword != null) mConfig.sslKeyPassword = conf.sslKeyPassword;
        return this;
    }

    synchronized public WsProxyServer config(InputStream in) {
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
                case "sslCert":
                    mConfig.sslCert = config.getProperty(name);
                    break;
                case "sslKey":
                    mConfig.sslKey = config.getProperty(name);
                    break;
                case "sslKeyPassword":
                    mConfig.sslKeyPassword = config.getProperty(name);
                    break;
                }
            }
        } catch (IOException ex) {
            sLogger.warn("Failed to load config\n", ex);
        }
        return this;
    }

    /**
     * Start the proxy server
     */
    synchronized public WsProxyServer start() {
        if (mChannelFuture != null) {
            sLogger.warn("already started");
            return this;
        }

        SslContext sslContext = null; // Make sure always update it
        if (mConfig.sslCert != null && mConfig.sslKey != null) {
            try {
                FileInputStream is = new FileInputStream(mConfig.sslCert);
                Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(is);
                sLogger.info("Cert s:{}", ((X509Certificate) cert).getSubjectX500Principal().getName());
                sLogger.info("     i:{}", ((X509Certificate) cert).getIssuerX500Principal().getName());
            } catch (FileNotFoundException | CertificateException e) {
                sLogger.warn("Failed to load certificate\n", e);
            }

            SslContextBuilder sslCtxBuilder = (mConfig.sslKeyPassword != null) ?
                    SslContextBuilder.forServer(new File(mConfig.sslCert), new File(mConfig.sslKey), mConfig.sslKeyPassword) :
                    SslContextBuilder.forServer(new File(mConfig.sslCert), new File(mConfig.sslKey));
            try {
                sslContext = sslCtxBuilder.build();
            } catch (SSLException ex) {
                sLogger.warn("Failed to init ssl\n", ex);
            }
        }

        SocketAddress address = new InetSocketAddress(mConfig.bindAddress, mConfig.bindPort);
        sLogger.trace("start address:{}", address);

        mChannelFuture = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new WsServerInitializer(sslContext, mWorkerGroup))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(address)
                .syncUninterruptibly();
        return this;
    }

    /**
     * Stop the proxy server
     */
    synchronized public WsProxyServer stop() {
        sLogger.trace("stop");
        if (mChannelFuture == null) {
            sLogger.warn("not started");
            return this;
        }
        mChannelFuture.channel()
                .close()
                .syncUninterruptibly();
        sLogger.trace("close sync");

        mChannelFuture.channel()
                .closeFuture()
                .syncUninterruptibly();
        sLogger.trace("close future sync");
        return this;
    }

    public int port() {
        return mConfig.bindPort;
    }

    public static void main(String[] args) {
        WsProxyServer server = new WsProxyServer();
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
            if ("--cert".equals(key)) {
                config.sslCert = args[idx++];
            }
            if ("--key".equals(key)) {
                config.sslKey = args[idx++];
            }
            if ("--password".equals(key)) {
                config.sslKeyPassword = args[idx++];
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
                System.out.println("Usage: WsTunnelServer [options]");
                System.out.println("    -a | --addr     Socket bind address, default 0.0.0.0");
                System.out.println("    -p | --port     Socket bind port, default 5081");
                System.out.println("    --cert          Cert file for SSL");
                System.out.println("    --key           Key file for SSL, in PKCS8 format");
                System.out.println("    --password      Password to access encrypted key");
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
