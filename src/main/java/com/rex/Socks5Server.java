package com.rex;

import com.rex.websocket.WsConnection;
import com.rex.websocket.WsServerPathInterceptor;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCounted;
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
 * Socks5 server
 */
public class Socks5Server {

    private static final Logger sLogger = LoggerFactory.getLogger(Socks5Server.class);

    private final EventLoopGroup mBossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup mWorkerGroup = new NioEventLoopGroup(); // Default use Runtime.getRuntime().availableProcessors() * 2

    private ChannelFuture mChannelFuture;

    private final List<WsConnection> mConnectionList = new ArrayList<>();

    public interface Callback {
        void onAdded(Socks5Server server, WsConnection conn);
        void onReceived(Socks5Server server, WsConnection conn, ByteBuffer data);
        void onRemoved(Socks5Server server, WsConnection conn);
    }
    private Callback mCallback;

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
    public Socks5Server() {
        sLogger.trace("<init>");
    }

    synchronized public Socks5Server config(Configuration conf) {
        if (conf.bindAddress != null) mConfig.bindAddress = conf.bindAddress;
        if (conf.bindPort != 0) mConfig.bindPort = conf.bindPort;
        return this;
    }

    synchronized public Socks5Server config(InputStream in) {
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
    synchronized public Socks5Server start() {
        if (mChannelFuture != null) {
            sLogger.warn("already started");
            return this;
        }

        SocketAddress address = new InetSocketAddress(mConfig.bindAddress, mConfig.bindPort);
        sLogger.trace("start address:{}", address);

        mChannelFuture = new ServerBootstrap()
                .group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override // ChannelInitializer
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new IdleStateHandler(15, 15, 0))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                        if (evt instanceof IdleStateEvent) {
                                            ctx.close();
                                        }
                                    }
                                })
                                .addLast(Socks5ServerEncoder.DEFAULT)
                                .addLast(new Socks5InitialRequestDecoder());

                        if (mConfig.authUser != null && mConfig.authPassword != null) {
                            ch.pipeline()
                                    .addLast(new SimpleChannelInboundHandler<DefaultSocks5InitialRequest>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5InitialRequest msg) throws Exception {
                                            if (msg.version().equals(SocksVersion.SOCKS5)) {
                                                ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
                                            } else {
                                                sLogger.warn("Invalid version {}", msg.version());
                                                ctx.close();
                                            }
                                        }
                                    })
                                    .addLast(new Socks5PasswordAuthRequestDecoder())
                                    .addLast(new SimpleChannelInboundHandler<DefaultSocks5PasswordAuthRequest>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5PasswordAuthRequest msg) throws Exception {
                                            if (msg.username().equals(mConfig.authUser) &&
                                                    msg.password().equals(mConfig.authPassword)) {
                                                ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                                            } else {
                                                ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE));
                                            }
                                        }
                                    });
                        } else {
                            ch.pipeline()
                                    .addLast(new SimpleChannelInboundHandler<DefaultSocks5InitialRequest>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5InitialRequest msg) throws Exception {
                                            if (msg.version().equals(SocksVersion.SOCKS5)) {
                                                ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
                                            } else {
                                                sLogger.warn("Invalid version {}", msg.version());
                                                ctx.close();
                                            }
                                        }
                                    });
                        }

                        ch.pipeline()
                                .addLast(new Socks5CommandRequestDecoder())
                                .addLast(new SimpleChannelInboundHandler<Socks5CommandRequest>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandRequest msg) throws Exception {
                                        if (msg.type().equals(Socks5CommandType.CONNECT)) {
                                            ChannelFuture future = new Bootstrap().group(mWorkerGroup)
                                                    .channel(NioSocketChannel.class)
                                                    .option(ChannelOption.TCP_NODELAY, true)
                                                    .handler(new ChannelInitializer<SocketChannel>() {
                                                        @Override
                                                        protected void initChannel(SocketChannel ch) throws Exception {
                                                            ch.pipeline().addLast(new BridgeChannelInboundHandlerAdapter(ctx.channel()));
                                                        }
                                                    })
                                                    .connect(msg.dstAddr(), msg.dstPort());

                                            future.addListener(new ChannelFutureListener() {
                                                public void operationComplete(final ChannelFuture future) throws Exception {
                                                    if (future.isSuccess()) {
                                                        ctx.pipeline().addLast(new BridgeChannelInboundHandlerAdapter(future.channel()));
                                                        ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4));
                                                    } else {
                                                        ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4));
                                                    }
                                                }

                                            });
                                        } else {
                                            sLogger.warn("Unsupported command type {}", msg.type());
                                        }
                                    }
                                });
                    }
                })
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(address)
                .syncUninterruptibly();
        return this;
    }

    /**
     * Stop the socks5 server
     */
    synchronized public Socks5Server stop() {
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

    public Socks5Server setCallback(Callback cb) {
        mCallback = cb;
        return this;
    }

    @ChannelHandler.Sharable
    public class BridgeChannelInboundHandlerAdapter extends ChannelInboundHandlerAdapter {
        private final Channel mTarget;
        public BridgeChannelInboundHandlerAdapter(Channel target) {
            mTarget = target;
        }
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            sLogger.debug("forwarding message:{}", msg);
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).retain();
            }
            mTarget.writeAndFlush(msg);
        }
    }

    public static void main(String[] args) {
        Socks5Server server = new Socks5Server();
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
