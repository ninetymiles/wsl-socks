package com.rex.proxy.socks.v5;

import com.rex.proxy.WslLocal;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Socks5PasswordAuthRequestHandlerTest {

    @Test
    public void testAuthSuccess() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();

        WslLocal.Configuration config = new WslLocal.Configuration();
        config.authUser = "user";
        config.authPassword = "password";
        channel.pipeline().addLast(new Socks5PasswordAuthRequestHandler(config));
        channel.writeInbound(new DefaultSocks5PasswordAuthRequest("user", "password"));

        Socks5PasswordAuthResponse response = channel.readOutbound();
        assertEquals(Socks5PasswordAuthStatus.SUCCESS, response.status());
    }

    @Test
    public void testAuthFailed() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();

        WslLocal.Configuration config = new WslLocal.Configuration();
        config.authUser = "user";
        config.authPassword = "password";
        channel.pipeline().addLast(new Socks5PasswordAuthRequestHandler(config));
        channel.writeInbound(new DefaultSocks5PasswordAuthRequest("user", "not_match"));

        Socks5PasswordAuthResponse response = channel.readOutbound();
        assertEquals(Socks5PasswordAuthStatus.FAILURE, response.status());
    }
}
