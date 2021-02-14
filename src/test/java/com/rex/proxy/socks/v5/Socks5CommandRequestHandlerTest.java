package com.rex.proxy.socks.v5;

import com.rex.proxy.WslLocal;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.v5.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class Socks5CommandRequestHandlerTest {

    private final Logger mLogger = LoggerFactory.getLogger(Socks5CommandRequestHandlerTest.class.getSimpleName());

    @Test
    public void testConnectDirectSuccess() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();
        mLogger.debug("Mock server [{}:{}]", server.getHostName(), server.getPort());

        EventLoopGroup group = new NioEventLoopGroup();

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new Socks5CommandRequestHandler(new WslLocal.Configuration()).eventLoop(group.next()));
        channel.writeInbound(new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT,
                Socks5AddressType.IPv4,
                "127.0.0.1",
                server.getPort()));

        Thread.sleep(100);

        DefaultSocks5CommandResponse response = channel.readOutbound();
        assertEquals(Socks5CommandStatus.SUCCESS, response.status());

        server.close();
    }

    @Test
    public void testConnectDirectFailed() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new Socks5CommandRequestHandler(new WslLocal.Configuration()).eventLoop(group.next()));
        channel.writeInbound(new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT,
                Socks5AddressType.IPv4,
                "127.0.0.1",
                7777));

        Thread.sleep(100);

        DefaultSocks5CommandResponse response = channel.readOutbound();
        assertEquals(Socks5CommandStatus.FAILURE, response.status());
    }

    @Test
    public void testConnectWsProxySuccess() throws Exception {

    }

    @Test
    public void testConnectWsProxyFailed() throws Exception {

    }

    @Test
    public void testBind() throws Exception {
        WslLocal.Configuration config = new WslLocal.Configuration();

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new Socks5CommandRequestHandler(config));
        channel.writeInbound(new DefaultSocks5CommandRequest(Socks5CommandType.BIND,
                Socks5AddressType.IPv4,
                "0.0.0.0",
                0));

        DefaultSocks5CommandResponse response = channel.readOutbound();
        assertEquals(Socks5CommandStatus.SUCCESS, response.status());
    }

    @Test
    public void testUdpAssociate() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(new Socks5CommandRequestHandler(new WslLocal.Configuration()).eventLoop(group.next()));
        channel.writeInbound(new DefaultSocks5CommandRequest(Socks5CommandType.UDP_ASSOCIATE,
                Socks5AddressType.IPv4,
                "0.0.0.0",
                0));

        DefaultSocks5CommandResponse response = channel.readOutbound();
        assertEquals(Socks5CommandStatus.SUCCESS, response.status());
        //mLogger.debug("response:{}", response);

        SocketException expect = null;
        try {
            new DatagramSocket(response.bndPort());
        } catch (SocketException ex) {
            // Failed to bind port will throw SocketException
            expect = ex;
        }
        assertNotNull(expect);
    }

    @Test
    public void testUdpAssociateAbort() throws Exception {
        // When TCP control channel disconnected, should also abort the udp binding
        EventLoopGroup group = new NioEventLoopGroup();

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(new Socks5CommandRequestHandler(new WslLocal.Configuration()).eventLoop(group.next()));
        channel.writeInbound(new DefaultSocks5CommandRequest(Socks5CommandType.UDP_ASSOCIATE,
                Socks5AddressType.IPv4,
                "0.0.0.0",
                0));

        DefaultSocks5CommandResponse response = channel.readOutbound();
        assertEquals(Socks5CommandStatus.SUCCESS, response.status());
        //mLogger.debug("response:{}", response);

        channel.close();

        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(response.bndPort());
        } catch (SocketException ex) {
            // Failed to bind port will throw SocketException
            mLogger.warn("Failed to bind port - {}", ex.getMessage());
        }
        assertNotNull(socket);
    }
}
