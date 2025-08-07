package com.rex.proxy.utils;

import com.rex.proxy.common.BridgeChannelHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EchoServerTest {

    private static final Logger sLogger = LoggerFactory.getLogger(EchoServerTest.class.getSimpleName());

    @Test
    public void testBasic() throws Exception {
        // Prepare echo server
        EchoServer server = new EchoServer()
                .start();

        // Connect to echo server with 3s timeout
        Socket client = new Socket();
        client.connect(new InetSocketAddress("127.0.0.1", server.port()), 3000);
        DataOutputStream out = new DataOutputStream(client.getOutputStream());
        DataInputStream in = new DataInputStream(client.getInputStream());

        // Send string content
        String payload = "HelloWorld!";
        out.writeUTF(payload);

        // Verify client may receive the same data
        assertEquals(payload, in.readUTF());

        // Close all
        client.close();
        server.stop();
    }

    @Test
    public void testSecured() throws Exception {
        // Prepare echo server
        EchoServer server = new EchoServer()
                .host("localhost")
                .start(true);

        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null, new X509TrustManager[] { new X509TrustAllManager() }, null);

        // Connect to echo server with 3s timeout
        InetSocketAddress addr = new InetSocketAddress("localhost", server.port());
        SSLSocket client = (SSLSocket) sslCtx.getSocketFactory().createSocket();
        client.setSoTimeout(3000);
        client.connect(addr, 3000);
        DataOutputStream out = new DataOutputStream(client.getOutputStream());
        DataInputStream in = new DataInputStream(client.getInputStream());

        // Send string content
        String payload = "HelloWorld!";
        out.writeUTF(payload);

        // Verify client may receive the same data
        assertEquals(payload, in.readUTF());

        // Close all
        client.close();
        server.stop();
    }

    @Test
    public void testChildListener() throws Exception {
        // Prepare echo server
        EchoServer.ChildListener listener = mock(EchoServer.ChildListener.class);
        EchoServer server = new EchoServer()
                .setChildListener(listener)
                .start();

        // Use Bootstrap to bridge a EmbeddedChannel, for async I/O
        EmbeddedChannel local = new EmbeddedChannel();
        ChannelFuture future = new Bootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(@NotNull SocketChannel remote) throws Exception {
                        //sLogger.trace("remote=<{}>", remote); // no localAddress nor remoteAddress
                        //remote.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG)); // Print outbound traffic
                        remote.pipeline().addLast(new BridgeChannelHandler(local));
                        local.pipeline().addLast(new BridgeChannelHandler(remote));
                    }
                })
                .connect("127.0.0.1", server.port())
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture cf) throws Exception {
                        //sLogger.trace("future:{}", cf);
                        if (cf.isSuccess()) {
                            sLogger.debug("Connect succeed");
                        } else {
                            sLogger.debug("Connect failed");
                        }
                    }
                });
        verify(listener, timeout(3000)).onOpen(any());

        // Echo server request and response
        local.writeInbound(Unpooled.wrappedBuffer("HelloWorld".getBytes()));
        verify(listener, timeout(3000)).onRead(any(), any());
        verify(listener, timeout(3000)).onWrite(any(), any());

        ByteBuf buf = local.readOutbound();
        assertEquals("HelloWorld", buf.toString(StandardCharsets.UTF_8));

        // Close client socket, should report child closed
        future.channel().close().sync();
        verify(listener, timeout(3000)).onClosed(any());

        // Shutdown everything
        server.stop();
    }
}
