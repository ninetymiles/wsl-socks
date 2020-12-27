package com.rex.proxy.socks.v5;

import com.rex.proxy.WslLocal;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.v5.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Socks5CommandRequestHandlerTest {

    //@Test
    public void testConnectDirectSuccess() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();

        WslLocal.Configuration config = new WslLocal.Configuration();
        channel.pipeline().addLast(new Socks5CommandRequestHandler(config));
        channel.writeInbound(new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT,
                Socks5AddressType.IPv4,
                "127.0.0.1",
                server.getPort()));

        DefaultSocks5CommandResponse response = channel.readOutbound();
        assertEquals(Socks5CommandStatus.SUCCESS, response.status());

        server.close();
    }

    @Test
    public void testConnectDirectFailed() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();

        WslLocal.Configuration config = new WslLocal.Configuration();
        channel.pipeline().addLast(new Socks5CommandRequestHandler(config));
        channel.writeInbound(new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT,
                Socks5AddressType.IPv4,
                "127.0.0.1",
                7777));

        DefaultSocks5CommandResponse response = channel.readOutbound();
        assertEquals(Socks5CommandStatus.FAILURE, response.status());
    }

    @Test
    public void testConnectWsProxySuccess() throws Exception {

    }

    @Test
    public void testConnectWsProxyFailed() throws Exception {

    }

    //@Test
    public void testBind() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();

        WslLocal.Configuration config = new WslLocal.Configuration();
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
    }
}
