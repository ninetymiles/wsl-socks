package com.rex.proxy.http;

import com.rex.proxy.WslLocal;
import com.rex.proxy.socks.SocksProxyInitializer;
import com.rex.proxy.websocket.WsClientInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handle received http requests
 * Connect target host with websocket protocol
 */
@ChannelHandler.Sharable
public class HttpServerPathInterceptor extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger sLogger = LoggerFactory.getLogger(HttpServerPathInterceptor.class);

    public static final String REGEX_CONNECT = "(.+):([0-9]+)"; // server.example.com:443 or 127.0.0.1:443

    private final EventLoopGroup mWorkerGroup;
    private final WslLocal.Configuration mConfig;

    public HttpServerPathInterceptor(EventLoopGroup group, WslLocal.Configuration config) {
        sLogger.trace("<init>");
        mWorkerGroup = group;
        mConfig = config;
    }

    @Override // SimpleChannelInboundHandler
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        sLogger.trace("method:<{}> uri:<{}>", request.method(), request.uri());

        if (request.method() != HttpMethod.CONNECT) {
            sLogger.warn("Method-not-allowed <{}> from {}", request.method(),  ctx.channel().remoteAddress());
            ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.METHOD_NOT_ALLOWED))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }

        String  addr = null;
        int     port = -1;
        Pattern pattern = Pattern.compile(REGEX_CONNECT);
        Matcher matcher = pattern.matcher(request.uri());
        if (matcher.find()) {
            addr = matcher.group(1);
            port = Integer.parseInt(matcher.group(2));
            sLogger.debug("Request <{}:{}>", addr, port);
        }
        if (addr == null || port < 0 || port > 65535) {
            sLogger.warn("Bad-request <{}:{}> from {}", addr, port, ctx.channel().remoteAddress());
            ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.BAD_REQUEST))
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }

        if (mConfig.authUser != null || mConfig.authPassword != null) {
            String auth = request.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION);
            if (auth != null && !auth.isEmpty()) {
                String credential = mConfig.authUser + ":" + mConfig.authPassword;
                ByteBuf credential64 = Base64.encode(Unpooled.wrappedBuffer(credential.getBytes(StandardCharsets.UTF_8)), false); // Avoid use java.util.Base64 introduced in java11, device may run on java8
                String credentialFull = "Basic " + credential64.toString(StandardCharsets.UTF_8);
                //sLogger.debug("Proxy Authentication authorization=<{}> credential=<{}>", auth, credentialFull);
                if (!credentialFull.equalsIgnoreCase(auth)) {
                    sLogger.warn("Proxy Authentication Failed <{}:{}> from {}", addr, port, ctx.channel().remoteAddress());
                    ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), new HttpResponseStatus(407, "Proxy Authentication Failed")))
                            .addListener(ChannelFutureListener.CLOSE);
                }
            } else {
                sLogger.warn("Proxy Authentication Required <{}:{}> from {}", addr, port, ctx.channel().remoteAddress());
                ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }

        try {
            InetSocketAddress address = InetSocketAddress.createUnresolved(addr, port);
            sLogger.debug("Connect <{}>", address);

            ChannelInboundHandlerAdapter handler = new SimpleUserEventChannelHandler<RemoteStateEvent>() {
                @Override
                protected void eventReceived(ChannelHandlerContext remoteCtx, RemoteStateEvent evt) throws Exception {
                    sLogger.trace("evt:{} remoteCtx:{}", evt, remoteCtx);
                    switch (evt) {
                    case REMOTE_READY:
                        ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK));
                        if (ctx.pipeline().get(HttpServerCodec.class) != null) {
                            ctx.pipeline().remove(HttpServerCodec.class);
                        }
                        if (ctx.pipeline().get(HttpObjectAggregator.class) != null) {
                            ctx.pipeline().remove(HttpObjectAggregator.class);
                        }
                        ctx.pipeline().remove(HttpServerPathInterceptor.this);
                        remoteCtx.pipeline().remove(this);
                        //sLogger.trace("FINAL Local pipeline:{}", ctx.pipeline());
                        //sLogger.trace("FINAL Remote pipeline:{}", remoteCtx.pipeline());
                        break;
                    case REMOTE_FAILED:
                        ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.NOT_ACCEPTABLE))
                                .addListener(ChannelFutureListener.CLOSE);
                        break;
                    default:
                        break;
                    }
                }
            };

            new Bootstrap()
                    .group(mWorkerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler((mConfig.proxyUri != null) ?
                            new WsClientInitializer(mConfig, ctx, addr, port) :
                            new SocksProxyInitializer(mConfig, ctx)) // FIXME: Rename to BridgeInitializer, remove the socks prefix
                    .connect(address)
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            sLogger.trace("future:{}", future);
                            if (future.isSuccess()) {
                                sLogger.debug("Connect success {}", future.channel());
                                future.channel()
                                        .pipeline()
                                        .addLast(handler);
                                //sLogger.trace("Remote pipeline:{}", future.channel().pipeline());
                            } else {
                                sLogger.warn("Connect failed {}, reason:\n", future.channel(), future.cause());
                                ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.NOT_ACCEPTABLE))
                                        .addListener(ChannelFutureListener.CLOSE);
                            }
                        }
                    });
            request.retain(); // Increase reference count, avoid recycle before bootstrap connect completed
        } catch (Exception ex) {
            sLogger.warn("Not-acceptable <{}:{}> from {} - {}", addr, port, ctx.channel().remoteAddress(), ex.getMessage());
            ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.NOT_ACCEPTABLE))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override // SimpleChannelInboundHandler
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //sLogger.warn("HttpServerPathInterceptor caught exception\n", cause);
        sLogger.warn("{}", cause.toString());
        ctx.close();
    }

    public enum RemoteStateEvent {
        REMOTE_READY,
        REMOTE_FAILED
    }
}
