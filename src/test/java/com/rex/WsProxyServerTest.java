package com.rex;

import com.google.gson.Gson;
import com.rex.utils.AllowAllHostnameVerifier;
import com.rex.utils.X509TrustAllManager;
import com.rex.websocket.control.ControlMessage;
import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.ByteString;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class WsProxyServerTest {

    @Test
    public void testConfigPort() throws Exception {
        final int port = 1234;
        WsProxyServer server = new WsProxyServer()
                .config(new WsProxyServer.Configuration("127.0.0.1", port))
                .start();

        URLConnection conn = new URL("http://127.0.0.1:" + port + "/")
                .openConnection();
        HttpURLConnection connHttp = (HttpURLConnection) conn;
        assertEquals(404, connHttp.getResponseCode());
        assertEquals("Not Found", connHttp.getResponseMessage());

        server.stop();
    }

    @Test
    public void testConfigFile() throws Exception {
        // getClass().getResourceAsStream() point to build/classes/java/test/
        // ClassLoader.getSystemResourceAsStream() point to build/resources/test/
        WsProxyServer server = new WsProxyServer()
                .config(ClassLoader.getSystemResourceAsStream("config_127.0.0.1_4321.properties"))
                .start();

        URLConnection conn = new URL("http://127.0.0.1:4321/")
                .openConnection();
        HttpURLConnection connHttp = (HttpURLConnection) conn;
        assertEquals(404, connHttp.getResponseCode());

        server.stop();
    }

    @Test
    public void testSSL() throws Exception {
        WsProxyServer server = new WsProxyServer()
                .config(new WsProxyServer.Configuration("127.0.0.1", 1234,
                        ClassLoader.getSystemResource("test.cert.pem").getFile(),
                        ClassLoader.getSystemResource("test.key.p8.pem").getFile()))
                .start();

        URLConnection conn = new URL("https://127.0.0.1:" + server.port() + "/")
                .openConnection();


        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new X509TrustManager[] { new X509TrustAllManager() }, null);

        HttpsURLConnection connHttps = (HttpsURLConnection) conn;
        connHttps.setSSLSocketFactory(ctx.getSocketFactory());
        connHttps.setHostnameVerifier(new AllowAllHostnameVerifier());

        assertEquals(404, connHttps.getResponseCode());

        server.stop();
    }

    @Test
    public void testSSLWithEncryptedKey() throws Exception {
        WsProxyServer server = new WsProxyServer()
                .config(new WsProxyServer.Configuration("127.0.0.1", 1234,
                        ClassLoader.getSystemResource("test.cert.pem").getFile(),
                        ClassLoader.getSystemResource("test.key.p8.encrypted.pem").getFile(),
                        "TestOnly"))
                .start();

        server.stop();
    }

    @Test
    public void testWebsocket() throws Exception {
        Gson gson = new Gson();
        WsProxyServer server = new WsProxyServer().start();

        WebSocketListener listener = mock(WebSocketListener.class);
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        Request request = new Request.Builder()
                .url("ws://127.0.0.1:" + server.port() + "/ws")
                .build();
        WebSocket ws = client.newWebSocket(request, listener);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(listener, timeout(1000)).onOpen(eq(ws), response.capture());


        ControlMessage msg = new ControlMessage();
        msg.type = "request";
        msg.action = "echo";
        ws.send(gson.toJson(msg));

        ArgumentCaptor<String> respTextMsg = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(1000)).onMessage(eq(ws), respTextMsg.capture());
        msg = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("response", msg.type);
        assertEquals("echo", msg.action);

        server.stop();
    }

    @Test
    public void testSecuredWebsocket() throws Exception {
        Gson gson = new Gson();
        WsProxyServer server = new WsProxyServer()
                .config(new WsProxyServer.Configuration("127.0.0.1", 1234,
                        ClassLoader.getSystemResource("test.cert.pem").getFile(),
                        ClassLoader.getSystemResource("test.key.p8.pem").getFile()))
                .start();

        X509TrustManager trustManager = new X509TrustAllManager();
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] { trustManager }, null);

        WebSocketListener listener = mock(WebSocketListener.class);
        OkHttpClient client = new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                .hostnameVerifier(new AllowAllHostnameVerifier())
                .build();
        Request request = new Request.Builder()
                .url("wss://127.0.0.1:" + server.port() + "/ws")
                .build();
        WebSocket ws = client.newWebSocket(request, listener);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(listener, timeout(1000)).onOpen(eq(ws), response.capture());


        ControlMessage msg = new ControlMessage();
        msg.type = "request";
        msg.action = "echo";
        ws.send(gson.toJson(msg));

        ArgumentCaptor<String> respTextMsg = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(1000)).onMessage(eq(ws), respTextMsg.capture());
        msg = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("response", msg.type);
        assertEquals("echo", msg.action);

        server.stop();
    }

    @Test
    public void testProxy() throws Exception {
        Gson gson = new Gson();
        MockWebServer httpServer = new MockWebServer();
        httpServer.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        httpServer.start();

        WsProxyServer server = new WsProxyServer()
                .start();

        WebSocketListener listener = mock(WebSocketListener.class);
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        Request request = new Request.Builder()
                .url("ws://127.0.0.1:" + server.port() + "/ws")
                .build();
        WebSocket ws = client.newWebSocket(request, listener);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(listener, timeout(1000)).onOpen(eq(ws), response.capture());

        ControlMessage msg = new ControlMessage();
        msg.type = "request";
        msg.action = "connect";
        msg.address = Inet4Address.getLoopbackAddress();
        msg.port = httpServer.getPort();
        ws.send(gson.toJson(msg));

        ArgumentCaptor<String> respTextMsg = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(1000)).onMessage(eq(ws), respTextMsg.capture());
        msg = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("response", msg.type);
        assertEquals("success", msg.action);

        StringBuffer sb = new StringBuffer();
        sb.append("GET / HTTP/1.1\r\n")
                .append("\r\n");
        ws.send(ByteString.of(sb.toString().getBytes()));

        RecordedRequest recordedReq = httpServer.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", recordedReq.getMethod());

        ArgumentCaptor<ByteString> respByteMsg = ArgumentCaptor.forClass(ByteString.class);
        verify(listener, timeout(1000)).onMessage(eq(ws), respByteMsg.capture());
        assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 11\r\n\r\nHelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(respByteMsg.getValue().asByteBuffer())
                .toString());

        server.stop();
        httpServer.shutdown();
    }
}
