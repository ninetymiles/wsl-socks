package com.rex.proxy.websocket;

import com.rex.proxy.WslServer;
import com.rex.proxy.websocket.control.ControlAuthBuilder;
import com.rex.proxy.websocket.control.ControlMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Random;

/**
 * A websocket proxy connection
 */
@ChannelHandler.Sharable
public class WsProxyControlHandler extends SimpleChannelInboundHandler<ControlMessage> {

    private static final Logger sLogger = LoggerFactory.getLogger(WsProxyControlHandler.class);

    private final EventLoopGroup mWorkerGroup;
    private final WslServer.Configuration mConfig;
    private final byte[] mNonce;
    private Channel mChannel;

    public WsProxyControlHandler(EventLoopGroup group, WslServer.Configuration config) {
        sLogger.trace("<init>");
        mWorkerGroup = group;
        mConfig = config;
        mNonce = new byte[32]; // 256bit nonce long enough
        new Random().nextBytes(mNonce);
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, ControlMessage msg) throws Exception {
        //sLogger.trace("msg:{}", new Gson().toJson(msg));
        if ("request".equalsIgnoreCase(msg.type) && "connect".equalsIgnoreCase(msg.action)) {
            if (mConfig.proxyUid != null) {
                String credential = new ControlAuthBuilder()
                        .setSecret(mConfig.proxyUid)
                        .setNonce(mNonce)
                        .setAddress(msg.address)
                        .setPort(msg.port)
                        .build();

                sLogger.trace("credential:{} token:{}", credential, msg.token);

                if (! credential.equals(msg.token)) {
                    sLogger.debug("proxy {}:{} reject {}", msg.address, msg.port, ctx.channel().remoteAddress());

                    ControlMessage resp = new ControlMessage();
                    resp.type = "response";
                    resp.action = "reject";
                    ctx.writeAndFlush(resp);
                    return;
                }
            }

            Bootstrap bootstrap = new Bootstrap()
                    .group(mWorkerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // XXX: ch.remoteAddress always null here
                            // connect future will get valid remote address
                            sLogger.info("proxy {} - {}", ctx.channel().remoteAddress(), ch.remoteAddress());
                            //ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print data in tunnel
                            ch.pipeline().addLast(new WsProxyRawToWs(ctx.channel()));
                            ctx.pipeline().addLast(new WsProxyWsToRaw(ch));
                        }
                    });

            bootstrap.connect(msg.address, msg.port)
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            sLogger.debug("proxy connect {}:{} {}", msg.address, msg.port, future.isSuccess() ? "success" : "failure");
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

    @Override // SimpleChannelInboundHandler
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        sLogger.trace("event:{}", evt);
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete event = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            mChannel = ctx.channel();
            sLogger.info("upgrade {} subprotocol {}", mChannel.remoteAddress(), event.selectedSubprotocol());

            ControlMessage msg = new ControlMessage();
            msg.type = "hello";
            if (mConfig.proxyUid != null) {
                msg.action = "hs256";
                msg.token  = Base64.getEncoder().encodeToString(mNonce);
                sLogger.trace("nonce:{}", msg.token);
            }
            ctx.writeAndFlush(msg);

            ctx.channel()
                    .closeFuture()
                    .addListener(mCloseListener);
        }
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("connection exception\n", cause);
        sLogger.warn("{}", cause.toString());
        if (mChannel.isActive()) {
            mChannel.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    private final ChannelFutureListener mCloseListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            sLogger.warn("connection lost {}", mChannel.remoteAddress());
        }
    };
}
