package com.rex.proxy.http;

import com.google.gson.Gson;
import com.rex.proxy.WslLocal;
import com.rex.proxy.utils.EchoServer;
import com.rex.proxy.websocket.control.ControlMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class HttpServerPathInterceptorTest {

    private static final Logger sLogger = LoggerFactory.getLogger(HttpServerPathInterceptorTest.class.getSimpleName());

    @Test
    public void testProxy() throws Exception {
        EchoServer.ChildListener listener = mock(EchoServer.ChildListener.class);
        EchoServer echo = new EchoServer();
        echo.setChildListener(listener);
        echo.start();
        sLogger.trace("Echo server: {}", echo.port());

        WslLocal.Configuration config = new WslLocal.Configuration();
        EventLoopGroup group = new NioEventLoopGroup();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(new HttpServerPathInterceptor(config).group(group));

        String url = "127.0.0.1:" + echo.port();
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, url);
        sLogger.trace("Send request to proxy server");
        channel.writeInbound(request);
        verify(listener, timeout(3000)).onOpen(any());

        sLogger.trace("Read response from proxy server");
        FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());

        sLogger.trace("Send hello to echo server");
        channel.writeInbound(Unpooled.wrappedBuffer("HelloWorld!".getBytes(StandardCharsets.UTF_8)));
        verify(listener, timeout(3000)).onRead(any(), any());

        sLogger.trace("Read response from echo server");
        ByteBuf frame = channel.readOutbound();
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(frame.nioBuffer())
                .toString());

        // Shutdown everything
        echo.stop();
    }

    @Test
    public void testAuthorization() throws Exception {
        EchoServer.ChildListener listener = mock(EchoServer.ChildListener.class);
        EchoServer echo = new EchoServer();
        echo.setChildListener(listener);
        echo.start();
        sLogger.trace("Echo server: {}", echo.port());

        WslLocal.Configuration config = new WslLocal.Configuration();
        config.authUser = "account";
        config.authPassword = "password";
        EventLoopGroup group = new NioEventLoopGroup();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(new HttpServerPathInterceptor(config).group(group));

        String crednetial = new CredentialFactoryBasic()
                .create(config.authUser, config.authPassword);
        String url = "127.0.0.1:" + echo.port();
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, url);
        request.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, crednetial);
        sLogger.trace("Send request to proxy server");
        channel.writeInbound(request);
        verify(listener, timeout(3000)).onOpen(any());

        sLogger.trace("Read response from proxy server");
        FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());

        // Shutdown everything
        echo.stop();
    }

    @Test
    public void testBridgeWebsocket() throws Exception {
        Gson gson = new Gson();
        WebSocketListener listener = mock(WebSocketListener.class);
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .withWebSocketUpgrade(listener)
                .setHeader("Sec-WebSocket-Protocol", "com.rex.websocket.protocol.proxy2")); // TODO: Share the const sub protocol between wsclient and wsserver
        server.start();
        sLogger.trace("MockServer: {}", server.url("/"));

        WslLocal.Configuration config = new WslLocal.Configuration();
        config.proxyUri = new URI("ws://localhost:" + server.getPort());
        EventLoopGroup group = new NioEventLoopGroup();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(new HttpServerPathInterceptor(config).group(group));

        String targetHost = "some.host";
        int targetPort = 12345;
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, targetHost + ":" + targetPort);
        request.headers().set("HOST", targetHost);
        sLogger.trace("Send request to proxy server");
        channel.writeInbound(request);


        ArgumentCaptor<WebSocket> ws = ArgumentCaptor.forClass(WebSocket.class);
        verify(listener, timeout(3000)).onOpen(ws.capture(), any());

        // WslServer handshake without auth
        ControlMessage msg = new ControlMessage();
        msg.type = "hello";
        ws.getValue().send(gson.toJson(msg));

        // WslLocal request tunnel to target address
        ArgumentCaptor<String> ack = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(3000)).onMessage(any(), ack.capture());
        msg = gson.fromJson(ack.getAllValues().get(0), ControlMessage.class);
        assertEquals("request", msg.type);
        assertEquals("connect", msg.action);
        assertEquals(targetHost, msg.address);
        assertEquals(Integer.valueOf(targetPort), msg.port);

        // WslServer accept the request to allow WslLocal set up the bridge
        msg = new ControlMessage();
        msg.type = "response";
        msg.action = "success";
        ws.getValue().send(gson.toJson(msg));

        Thread.sleep(1000); // Let server can handle the RemoteReady event and send response
        sLogger.trace("Read response from proxy server");
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