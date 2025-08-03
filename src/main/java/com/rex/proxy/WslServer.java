package com.rex.proxy;

import com.rex.proxy.websocket.WsServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * WebSocket proxy server
 */
public class WslServer {

    private static final Logger sLogger = LoggerFactory.getLogger(WslServer.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2

    private ChannelFuture mChannelFuture;

    public static class Configuration {
        public String bindAddress;
        public Integer bindPort;
        public Boolean ssl;
        public String sslCert;
        public String sslKey; // In PKCS8 format
        public String sslKeyPassword; // Leave it null if key not encrypted
        public String proxyUid; // Leave it null if you do not need auth
        public String proxyPath; // Leave it null if accept all http path upgrading
        public Configuration() {
        }
        public Configuration(int port) {
            bindPort = port;
        }
        public Configuration(String addr, int port) {
            this(port);
            bindAddress = addr;
        }
        public Configuration(String addr, int port, String cert, String key) {
            this(addr, port);
            ssl = true;
            sslCert = cert;
            sslKey = key;
        }
        public Configuration(String addr, int port, String cert, String key, String keyPassword) {
            this(addr, port, cert, key);
            sslKeyPassword = keyPassword;
        }
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("<@");
            builder.append(Integer.toHexString(hashCode()));
            builder.append(" bindAddress:").append(bindAddress);
            builder.append(" bindPort:").append(bindPort);
            builder.append(" ssl:").append(ssl);
            builder.append(" sslCert:").append(sslCert);
            builder.append(" sslKey:").append(sslKey);
            builder.append(" sslKeyPassword:").append(sslKeyPassword);
            builder.append(" proxyUid:").append(proxyUid);
            builder.append(" proxyPath:").append(proxyPath);
            builder.append(">");
            return builder.toString();
        }
    }
    private final Configuration mConfig = new Configuration("0.0.0.0", 9777);

    /**
     * Construct the server
     */
    public WslServer() {
        sLogger.trace("<init>");
    }

    synchronized public WslServer config(Configuration conf) {
        if (conf.bindAddress != null) mConfig.bindAddress = conf.bindAddress;
        if (conf.bindPort != null) mConfig.bindPort = conf.bindPort;
        if (conf.ssl != null) mConfig.ssl = conf.ssl;
        if (conf.sslCert != null) mConfig.sslCert = conf.sslCert;
        if (conf.sslKey != null) mConfig.sslKey = conf.sslKey;
        if (conf.sslKeyPassword != null) mConfig.sslKeyPassword = conf.sslKeyPassword;
        if (conf.proxyUid != null) mConfig.proxyUid = conf.proxyUid;
        if (conf.proxyPath != null) mConfig.proxyPath = conf.proxyPath;
        return this;
    }

    /**
     * Start the proxy server
     */
    synchronized public WslServer start() {
        if (mChannelFuture != null) {
            sLogger.warn("already started");
            return this;
        }

        SslContext sslContext = null; // Make sure always update it
        if (Boolean.TRUE.equals(mConfig.ssl)) {
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
            } else {
                try {
                    SelfSignedCertificate ssc = new SelfSignedCertificate();
                    sLogger.info("Cert s:{}", ssc.cert().getSubjectX500Principal().getName());
                    sLogger.info("     i:{}", ssc.cert().getIssuerX500Principal().getName());
                    sslContext = SslContextBuilder.forServer(ssc.key(), ssc.cert()).build();
                } catch (CertificateException ex) {
                    sLogger.warn("Failed to generate self-signed certificate\n", ex);
                } catch (SSLException ex) {
                    sLogger.warn("Failed to init ssl\n", ex);
                }
            }
        }

        SocketAddress address = new InetSocketAddress(mConfig.bindAddress, mConfig.bindPort);
        sLogger.trace("start address:{}", address);

        mChannelFuture = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childHandler(new WsServerInitializer(mWorkerGroup, mConfig, sslContext))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(address)
                .syncUninterruptibly();

        InetSocketAddress sockAddr = (InetSocketAddress) mChannelFuture.channel().localAddress();
        sLogger.trace("started address={}:{}", sockAddr.getHostString(), sockAddr.getPort());
        return this;
    }

    /**
     * Stop the proxy server
     */
    synchronized public WslServer stop() {
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
        try {
            return ((InetSocketAddress) mChannelFuture.channel().localAddress()).getPort();
        } catch (Exception ex) {
            sLogger.warn("Failed to get port");
        }
        return mConfig.bindPort;
    }
}
