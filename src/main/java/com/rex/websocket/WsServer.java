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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * WebSocket server
 */
public class WsServer {

    private static final Logger sLogger = LoggerFactory.getLogger(WsServer.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2

    private ChannelFuture mChannelFuture;
    private SslContext mSslContext;

    private final List<WsConnection> mConnectionList = new ArrayList<>();

    public interface Callback {
        void onAdded(WsServer server, WsConnection conn);
        void onReceived(WsServer server, WsConnection conn, ByteBuffer data);
        void onRemoved(WsServer server, WsConnection conn);
    }
    private Callback mCallback;

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
    private Configuration mConfig = new Configuration("0.0.0.0", 9787); // WSTP in T9 keyboard

    /**
     * Construct the server
     */
    public WsServer() {
        sLogger.trace("<init>");
    }

    synchronized public WsServer config(Configuration conf) {
        if (conf.bindAddress != null) mConfig.bindAddress = conf.bindAddress;
        if (conf.bindPort != 0) mConfig.bindPort = conf.bindPort;
        if (conf.sslCert != null) mConfig.sslCert = conf.sslCert;
        if (conf.sslKey != null) mConfig.sslKey = conf.sslKey;
        if (conf.sslKeyPassword != null) mConfig.sslKeyPassword = conf.sslKeyPassword;
        return this;
    }

    synchronized public WsServer config(InputStream in) {
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
     * Start the websocket server
     */
    synchronized public WsServer start() {
        if (mChannelFuture != null) {
            sLogger.warn("already started");
            return this;
        }

        mSslContext = null; // Make sure always update it
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
                mSslContext = sslCtxBuilder.build();
            } catch (SSLException ex) {
                sLogger.warn("Failed to init ssl\n", ex);
            }
        }

        SocketAddress address = new InetSocketAddress(mConfig.bindAddress, mConfig.bindPort);
        sLogger.trace("start address:{}", address);

        mChannelFuture = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override // ChannelInitializer
                    protected void initChannel(SocketChannel ch) throws Exception {
                        if (mSslContext != null) {
                            ch.pipeline().addLast(mSslContext.newHandler(ch.alloc()));
                        }
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1 << 16)) // 65536
                                .addLast(new WsServerPathInterceptor(mWorkerGroup));
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(address)
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
            for (WsConnection conn : mConnectionList) {
                conn.close();
            }
        }
        return this;
    }

    public int port() {
        return mConfig.bindPort;
    }

    public WsServer setCallback(Callback cb) {
        mCallback = cb;
        return this;
    }

    private WsConnection.Callback mConnCallback = new WsConnection.Callback() {
        @Override
        public void onConnected(WsConnection conn) {
            sLogger.trace("connection {} connect", conn);
            synchronized (mConnectionList) {
                mConnectionList.add(conn);
            }
            if (mCallback != null) {
                mCallback.onAdded(WsServer.this, conn);
            }
        }
        @Override
        public void onReceived(WsConnection conn, ByteBuffer data) {
            sLogger.trace("connection {} receive {}", conn, data.remaining());
            if (mCallback != null) {
                mCallback.onReceived(WsServer.this, conn, data);
            }
        }
        @Override
        public void onDisconnected(WsConnection conn) {
            sLogger.trace("connection {} disconnect", conn);
            synchronized (mConnectionList) {
                mConnectionList.remove(conn);
            }
            if (mCallback != null) {
                mCallback.onRemoved(WsServer.this, conn);
            }
        }
    };

    public static void main(String[] args) {
        WsServer server = new WsServer();
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
