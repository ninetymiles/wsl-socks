package com.rex.proxy.websocket;

import com.rex.proxy.WslServer;
import com.rex.proxy.websocket.control.ControlAuthBuilder;
import com.rex.proxy.websocket.control.ControlMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Random;

/**
 * A websocket proxy connection handler.
 * Supports both new authorization protocol (authenticate once per connection)
 * and legacy per-request authentication for backward compatibility.
 */
@ChannelHandler.Sharable
public class WsProxyControlHandler extends SimpleChannelInboundHandler<ControlMessage> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsProxyControlHandler.class);

    // Channel attribute to track authorization state
    private static final AttributeKey<Boolean> ATTR_AUTHORIZED = AttributeKey.valueOf("ws.authorized");
    private static final AttributeKey<byte[]> ATTR_NONCE = AttributeKey.valueOf("ws.nonce");

    private final EventLoopGroup mWorkerGroup;
    private final WslServer.Configuration mConfig;

    public WsProxyControlHandler(EventLoopGroup group, WslServer.Configuration config) {
        sLogger.trace("group={} config={}", group, config);
        mWorkerGroup = group;
        mConfig = config;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, ControlMessage msg) throws Exception {
        // sLogger.trace("ctx={} msg={}", ctx, new Gson().toJson(msg));

        // Handle authorization message (new protocol for connection pooling)
        if ("authorization".equalsIgnoreCase(msg.type)) {
            handleAuthorization(ctx, msg);
            return;
        }

        if ("request".equalsIgnoreCase(msg.type) && "connect".equalsIgnoreCase(msg.action)) {
            // Check if authentication is required
            if (mConfig.proxyUid != null) {
                Boolean authorized = ctx.channel().attr(ATTR_AUTHORIZED).get();

                // If connection is already authorized (new protocol), skip token validation
                if (Boolean.TRUE.equals(authorized)) {
                    sLogger.debug("Connection already authorized, skipping token validation");
                } else {
                    // Legacy mode or first request: validate token in request
                    byte[] nonce = ctx.channel().attr(ATTR_NONCE).get();
                    String credential = new ControlAuthBuilder()
                            .setSecret(mConfig.proxyUid)
                            .setNonce(nonce)
                            .setAddress(msg.address)
                            .setPort(msg.port)
                            .build();
                    sLogger.trace("credential:{} token:{}", credential, msg.token);

                    if (!credential.equals(msg.token)) {
                        sLogger.debug("reject proxy {} to address={} port={}", ctx.channel().remoteAddress(), msg.address, msg.port);

                        ControlMessage resp = new ControlMessage();
                        resp.type = "response";
                        resp.action = "reject";
                        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
                        return;
                    }
                }
            }

            sLogger.debug("proxy to address=<{}> port={}", msg.address, msg.port);

            // Support connection reuse: remove old handler if exists
            // This allows the same WebSocket connection to be reused for different requests
            if (ctx.pipeline().get(WsProxyWsToRaw.class) != null) {
                sLogger.debug("Remove old WsProxyWsToRaw handler for connection reuse");
                ctx.pipeline().remove(WsProxyWsToRaw.class);
            }

            new Bootstrap()
                    .group(mWorkerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000) // Add connection timeout
                    .option(ChannelOption.SO_KEEPALIVE, true) // Enable TCP keepalive
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // XXX: ch.remoteAddress always null here
                            // connect future will get valid remote address
                            sLogger.info("proxy {} - {}", ctx.channel().remoteAddress(), ch.remoteAddress());
                            //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print data in tunnel
                            ch.pipeline().addLast(new WsProxyRawToWs(ctx.channel()));
                            ctx.pipeline().addLast(new WsProxyWsToRaw(ch));

                            // Monitor remote socket closure to clean up handlers
                            ch.closeFuture().addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    sLogger.debug("Remote socket closed {}, cleaning up handlers", future.channel());
                                    // Remove the handler from WebSocket pipeline when remote closes
                                    if (ctx.pipeline().get(WsProxyWsToRaw.class) != null) {
                                        ctx.pipeline().remove(WsProxyWsToRaw.class);
                                    }
                                }
                            });
                        }
                    })
                    .connect(msg.address, msg.port)
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            // sLogger.trace("future={} channel={}", future, future.channel());
                            sLogger.debug("proxy connect {} {}", future.channel().remoteAddress(), future.isSuccess() ? "success" : "failure");
                            if (! ctx.channel().isActive()) {
                                return;
                            }

                            if (future.isSuccess()) {
                                ControlMessage msg = new ControlMessage();
                                msg.type = "response";
                                msg.action = "success";
                                ctx.writeAndFlush(msg);
                            } else {
                                ControlMessage msg = new ControlMessage();
                                msg.type = "response";
                                msg.action = "failure";
                                ctx.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE);
                            }
                        }
                    });
        } else if ("request".equalsIgnoreCase(msg.type) && "echo".equalsIgnoreCase(msg.action)) {
            msg.type = "response";
            ctx.writeAndFlush(msg);
        } else {
            sLogger.warn("Not supported message:{}", msg);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        sLogger.trace("ctx={}", ctx);

        // Generate nonce for this connection
        byte[] nonce = new byte[32]; // 256bit nonce long enough
        new Random().nextBytes(nonce);
        ctx.channel().attr(ATTR_NONCE).set(nonce);
        ctx.channel().attr(ATTR_AUTHORIZED).set(false);

        ControlMessage msg = new ControlMessage();
        msg.type = "hello";
        if (mConfig.proxyUid != null) {
            msg.action = "hs256";
            msg.token  = Base64.getEncoder().encodeToString(nonce);
            sLogger.trace("nonce:{}", msg.token);
        }
        ctx.writeAndFlush(msg);

        ctx.channel()
                .closeFuture()
                .addListener(mCloseListener);
    }

    /**
     * Handle authorization message from client (new protocol).
     * Client sends authorization once per WebSocket connection to avoid
     * sending tokens with every request.
     */
    private void handleAuthorization(ChannelHandlerContext ctx, ControlMessage msg) {
        if (mConfig.proxyUid == null) {
            sLogger.warn("Received authorization but auth not enabled");
            return;
        }

        byte[] nonce = ctx.channel().attr(ATTR_NONCE).get();

        // Build expected credential using only secret and nonce
        String expectedCredential = new ControlAuthBuilder()
                .setSecret(mConfig.proxyUid)
                .setNonce(nonce)
                .build();

        sLogger.trace("expectedCredential:{} receivedToken:{}", expectedCredential, msg.token);

        if (expectedCredential.equals(msg.token)) {
            // Authorization successful
            ctx.channel().attr(ATTR_AUTHORIZED).set(true);
            sLogger.info("WebSocket connection {} authorized successfully", ctx.channel().remoteAddress());

            ControlMessage resp = new ControlMessage();
            resp.type = "authorized";
            ctx.writeAndFlush(resp);
        } else {
            // Authorization failed
            sLogger.warn("Authorization failed for {}", ctx.channel().remoteAddress());

            ControlMessage resp = new ControlMessage();
            resp.type = "response";
            resp.action = "reject";
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // sLogger.trace("ctx={}", ctx, cause);
        sLogger.warn("connection exception={}", cause.toString());
        if (ctx.channel().isActive()) {
            ctx.channel().writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    private final ChannelFutureListener mCloseListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            //sLogger.trace("future={} channel={}", future, future.channel());
            sLogger.warn("connection lost {}", future.channel().remoteAddress());
        }
    };
}
