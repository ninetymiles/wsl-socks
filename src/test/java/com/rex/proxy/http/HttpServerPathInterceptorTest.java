package com.rex.proxy.http;

import com.rex.proxy.WslLocal;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class HttpServerPathInterceptorTest {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Test");

    @Test
    public void testProxy() throws Exception {
        WebSocketListener listener = mock(WebSocketListener.class);
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();

        EventLoopGroup group = new NioEventLoopGroup();
        WslLocal.Configuration config = new WslLocal.Configuration();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(new HttpServerPathInterceptor(group, config));

        String url = "127.0.0.1:" + server.getPort();
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, url);
        channel.writeInbound(request);
        verify(listener, timeout(3000)).onOpen(any(), any());

        FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());

        // Shutdown everything
        server.shutdown();
    }

    @Test
    public void testAuthorization() throws Exception {
        WebSocketListener listener = mock(WebSocketListener.class);
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();

        EventLoopGroup group = new NioEventLoopGroup();
        WslLocal.Configuration config = new WslLocal.Configuration();
        config.authUser = "account";
        config.authPassword = "password";
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(new HttpServerPathInterceptor(group, config));

        String crednetial = new CredentialFactoryBasic(config.authUser, config.authPassword)
                .create();
        String url = "127.0.0.1:" + server.getPort();
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, url);
        request.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, crednetial);
        channel.writeInbound(request);
        verify(listener, timeout(3000)).onOpen(any(), any());

        FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());

        // Shutdown everything
        server.shutdown();
    }

    @Test
    public void testBridgeWebsocket() throws Exception {
        WebSocketListener listener = mock(WebSocketListener.class);
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .withWebSocketUpgrade(listener)
                .setHeader("Sec-WebSocket-Protocol", "com.rex.websocket.protocol.proxy2")); // TODO: Share the const sub protocol between wsclient and wsserver
        server.start();

        EventLoopGroup group = new NioEventLoopGroup();
        WslLocal.Configuration config = new WslLocal.Configuration();
        config.proxyUri = new URI("ws://localhost:" + server.getPort());
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(new HttpServerPathInterceptor(group, config));

        String url = "127.0.0.1:" + server.getPort();
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, url);
        channel.writeInbound(request);
        verify(listener, timeout(3000)).onOpen(any(), any());

        FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());

        // Shutdown everything
        server.shutdown();
    }

    @Test
    public void testRegexConnect() {
        Pattern pattern = Pattern.compile(HttpServerPathInterceptor.REGEX_CONNECT);
        Matcher matcher = pattern.matcher("127.0.0.1:443");
        assertTrue(matcher.find());
        assertEquals("127.0.0.1", matcher.group(1));
        assertEquals("443", matcher.group(2));

        matcher = pattern.matcher("server.example.com:443");
        assertTrue(matcher.find());
        assertEquals("server.example.com", matcher.group(1));
        assertEquals("443", matcher.group(2));

//        // HttpURLConnection will use legacy GET/POST instead CONNECT tunnel
//        matcher = pattern.matcher("http://localhost:2000/info");
//        assertTrue(matcher.find());
//        assertEquals("localhost", matcher.group(1));
//        assertEquals("2000", matcher.group(2));
    }
}