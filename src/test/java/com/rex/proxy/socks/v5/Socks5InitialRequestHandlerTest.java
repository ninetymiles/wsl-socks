package com.rex.proxy.socks.v5;

import com.rex.proxy.WslLocal;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Socks5InitialRequestHandlerTest {

    @Test
    public void testPasswordAuth() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();

        WslLocal.Configuration config = new WslLocal.Configuration();
        config.authUser = "user";
        config.authPassword = "password";
        channel.pipeline().addLast(new Socks5InitialRequestHandler(config));
        channel.writeInbound(new DefaultSocks5InitialRequest(Socks5AuthMethod.PASSWORD));

        DefaultSocks5InitialResponse response = channel.readOutbound();
        assertEquals(Socks5AuthMethod.PASSWORD, response.authMethod());
    }

    @Test
    public void testNoAuth() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();

        WslLocal.Configuration config = new WslLocal.Configuration();
        channel.pipeline().addLast(new Socks5InitialRequestHandler(config));
        channel.writeInbound(new DefaultSocks5InitialRequest(Socks5AuthMethod.PASSWORD));

        DefaultSocks5InitialResponse response = channel.readOutbound();
        assertEquals(Socks5AuthMethod.NO_AUTH, response.authMethod());
    }
}
