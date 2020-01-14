package com.rex.proxy;

import com.google.gson.Gson;
import com.rex.proxy.utils.AllowAllHostnameVerifier;
import com.rex.proxy.utils.EchoServer;
import com.rex.proxy.utils.X509TrustAllManager;
import com.rex.proxy.websocket.control.ControlMessage;
import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

// TODO: Test connect timeout
// TODO: Test connection lost
// TODO: Test re-try
public class WslServerTest {

    @Test
    public void testConfigPort() throws Exception {
        final int port = 1234;
        WslServer server = new WslServer()
                .config(new WslServer.Configuration("127.0.0.1", port))
                .start();

        URLConnection conn = new URL("http://127.0.0.1:" + port + "/")
                .openConnection();
        HttpURLConnection connHttp = (HttpURLConnection) conn;

        assertEquals(400, connHttp.getResponseCode());
        assertEquals("Bad Request", connHttp.getResponseMessage());
        server.stop();
    }

    @Test
    public void testConfigFile() throws Exception {
        // getClass().getResourceAsStream() point to build/classes/java/test/
        // ClassLoader.getSystemResourceAsStream() point to build/resources/test/
        WslServer server = new WslServer()
                .config(ClassLoader.getSystemResourceAsStream("config_127.0.0.1_4321.properties"))
                .start();

        URLConnection conn = new URL("http://127.0.0.1:4321/")
                .openConnection();
        HttpURLConnection connHttp = (HttpURLConnection) conn;
        assertEquals(400, connHttp.getResponseCode());

        server.stop();
    }

    @Test
    public void testConfigPath() throws Exception {
        WslServer.Configuration config = new WslServer.Configuration();
        config.proxyPath = "/proxy";

        WslServer server = new WslServer()
                .config(config)
                .start();

        // Valid path will upgrade to websocket handshake and URLConnection may get http response 400 Bad Request
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + config.proxyPath)
                .openConnection();
        assertEquals(400, conn.getResponseCode());
        assertEquals("Bad Request", conn.getResponseMessage());

        // Invalid path will got http response 404 Not Found
        conn = (HttpURLConnection) new URL("http://127.0.0.1:" + server.port() + "/invalid")
                .openConnection();
        assertEquals(404, conn.getResponseCode());
        assertEquals("Not Found", conn.getResponseMessage());

        server.stop();
    }

    @Test
    public void testSSL() throws Exception {
        WslServer server = new WslServer()
                .config(new WslServer.Configuration("127.0.0.1", 1234,
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

        assertEquals(400, connHttps.getResponseCode());

        server.stop();
    }

    @Test
    public void testSSLWithEncryptedKey() throws Exception {
        WslServer server = new WslServer()
                .config(new WslServer.Configuration("127.0.0.1", 1234,
                        ClassLoader.getSystemResource("test.cert.pem").getFile(),
                        ClassLoader.getSystemResource("test.key.p8.encrypted.pem").getFile(),
                        "TestOnly"))
                .start();

        server.stop();
    }

    @Test
    public void testSSLWithSelfSignedCert() throws Exception {
        WslServer.Configuration conf = new WslServer.Configuration("127.0.0.1", 1234);
        conf.ssl = true;
        WslServer server = new WslServer()
                .config(conf)
                .start();

        URLConnection conn = new URL("https://127.0.0.1:" + server.port() + "/")
                .openConnection();


        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new X509TrustManager[] { new X509TrustAllManager() }, null);

        HttpsURLConnection connHttps = (HttpsURLConnection) conn;
        connHttps.setSSLSocketFactory(ctx.getSocketFactory());
        connHttps.setHostnameVerifier(new AllowAllHostnameVerifier());

        assertEquals(400, connHttps.getResponseCode());

        server.stop();
    }

    @Test
    public void testWebsocket() throws Exception {
        Gson gson = new Gson();
        WslServer server = new WslServer().start();

        WebSocketListener listener = mock(WebSocketListener.class);
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        Request request = new Request.Builder()
                .url("ws://127.0.0.1:" + server.port() + "/")
                .build();
        WebSocket ws = client.newWebSocket(request, listener);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(listener, timeout(Duration.ofMillis(1000))).onOpen(eq(ws), response.capture());


        ControlMessage msg = new ControlMessage();
        msg.type = "request";
        msg.action = "echo";
        ws.send(gson.toJson(msg));

        ArgumentCaptor<String> respTextMsg = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(Duration.ofMillis(1000)).times(2)).onMessage(eq(ws), respTextMsg.capture());

        assertEquals(2, respTextMsg.getAllValues().size());

        msg = gson.fromJson(respTextMsg.getAllValues().get(0), ControlMessage.class);
        assertEquals("hello", msg.type);

        msg = gson.fromJson(respTextMsg.getAllValues().get(1), ControlMessage.class);
        assertEquals("response", msg.type);
        assertEquals("echo", msg.action);

        server.stop();
    }

    @Test
    public void testSecuredWebsocket() throws Exception {
        Gson gson = new Gson();
        WslServer server = new WslServer()
                .config(new WslServer.Configuration("127.0.0.1", 1234,
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
                .url("wss://127.0.0.1:" + server.port() + "/")
                .build();
        WebSocket ws = client.newWebSocket(request, listener);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(listener, timeout(Duration.ofMillis(1000))).onOpen(eq(ws), response.capture());


        ControlMessage msg = new ControlMessage();
        msg.type = "request";
        msg.action = "echo";
        ws.send(gson.toJson(msg));

        ArgumentCaptor<String> respTextMsg = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(Duration.ofMillis(1000)).times(2)).onMessage(eq(ws), respTextMsg.capture());
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

        WslServer server = new WslServer()
                .start();

        WebSocketListener listener = mock(WebSocketListener.class);
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        Request request = new Request.Builder()
                .url("ws://127.0.0.1:" + server.port() + "/")
                .build();
        WebSocket ws = client.newWebSocket(request, listener);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(listener, timeout(Duration.ofMillis(1000))).onOpen(eq(ws), response.capture());

        ArgumentCaptor<String> respTextMsg = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(Duration.ofMillis(1000))).onMessage(eq(ws), respTextMsg.capture());
        ControlMessage resp = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("hello", resp.type);
        assertNull(resp.action);
        assertNull(resp.token);
        reset(listener);

        ControlMessage req = new ControlMessage();
        req.type = "request";
        req.action = "connect";
        req.address = "127.0.0.1";
        req.port = httpServer.getPort();
        ws.send(gson.toJson(req));

        verify(listener, timeout(Duration.ofMillis(1000))).onMessage(eq(ws), respTextMsg.capture());
        resp = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("response", resp.type);
        assertEquals("success", resp.action);

        StringBuffer sb = new StringBuffer()
                .append("GET / HTTP/1.1\r\n")
                .append("\r\n");
        ws.send(ByteString.of(sb.toString().getBytes()));

        RecordedRequest recordedReq = httpServer.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", recordedReq.getMethod());

        ArgumentCaptor<ByteString> respByteMsg = ArgumentCaptor.forClass(ByteString.class);
        verify(listener, after(Duration.ofMillis(1000)).atLeastOnce()).onMessage(eq(ws), respByteMsg.capture());
        assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 11\r\n\r\nHelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(respByteMsg.getValue().asByteBuffer())
                .toString());

        // https://tools.ietf.org/html/rfc6455#section-7.4
        ws.close(1000, "Normal Closure");

        server.stop();
        httpServer.shutdown();
    }

    @Test
    public void testProxyMultiStream() throws Exception {
        Gson gson = new Gson();
        MockWebServer httpServer1 = new MockWebServer();
        httpServer1.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld1"));
        httpServer1.start();

        MockWebServer httpServer2 = new MockWebServer();
        httpServer2.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld2"));
        httpServer2.start();

        WslServer server = new WslServer()
                .start();

        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        Request request = new Request.Builder()
                .url("ws://127.0.0.1:" + server.port() + "/")
                .build();

        WebSocketListener listener1 = mock(WebSocketListener.class);
        WebSocketListener listener2 = mock(WebSocketListener.class);
        WebSocket ws1 = client.newWebSocket(request, listener1);
        WebSocket ws2 = client.newWebSocket(request, listener2);

        ArgumentCaptor<Response> response1 = ArgumentCaptor.forClass(Response.class);
        ArgumentCaptor<Response> response2 = ArgumentCaptor.forClass(Response.class);
        verify(listener1, timeout(Duration.ofMillis(1000))).onOpen(eq(ws1), response1.capture());
        verify(listener2, timeout(Duration.ofMillis(1000))).onOpen(eq(ws2), response2.capture());

        ControlMessage msg1 = new ControlMessage();
        msg1.type = "request";
        msg1.action = "connect";
        msg1.address = "127.0.0.1";
        msg1.port = httpServer1.getPort();
        ws1.send(gson.toJson(msg1));

        ControlMessage msg2 = new ControlMessage();
        msg2.type = "request";
        msg2.action = "connect";
        msg2.address = "127.0.0.1";
        msg2.port = httpServer2.getPort();
        ws2.send(gson.toJson(msg2));

        ArgumentCaptor<String> respTextMsg = ArgumentCaptor.forClass(String.class);
        verify(listener1, timeout(Duration.ofMillis(1000)).times(2)).onMessage(eq(ws1), respTextMsg.capture());
        msg1 = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("response", msg1.type);
        assertEquals("success", msg1.action);

        verify(listener2, timeout(Duration.ofMillis(1000)).times(2)).onMessage(eq(ws2), respTextMsg.capture());
        msg2 = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("response", msg2.type);
        assertEquals("success", msg2.action);

        StringBuffer sb = new StringBuffer()
                .append("GET / HTTP/1.1\r\n")
                .append("\r\n");
        ws1.send(ByteString.of(sb.toString().getBytes()));
        ws2.send(ByteString.of(sb.toString().getBytes()));

        ArgumentCaptor<ByteString> respByteMsg1 = ArgumentCaptor.forClass(ByteString.class);
        verify(listener1, timeout(Duration.ofMillis(1000))).onMessage(eq(ws1), respByteMsg1.capture());
        assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 11\r\n\r\nHelloWorld1", StandardCharsets.UTF_8
                .newDecoder()
                .decode(respByteMsg1.getValue().asByteBuffer())
                .toString());

        ArgumentCaptor<ByteString> respByteMsg2 = ArgumentCaptor.forClass(ByteString.class);
        verify(listener2, timeout(Duration.ofMillis(1000))).onMessage(eq(ws2), respByteMsg2.capture());
        assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 11\r\n\r\nHelloWorld2", StandardCharsets.UTF_8
                .newDecoder()
                .decode(respByteMsg2.getValue().asByteBuffer())
                .toString());

        ws1.close(1000, "NormalClosure");
        ws2.close(1000, "NormalClosure");

        server.stop();
        httpServer1.shutdown();
        httpServer2.shutdown();
    }

    @Test
    public void testProxyLargeFrame() throws Exception {
        Gson gson = new Gson();
        EchoServer echoServer = new EchoServer().start(false);
        WslServer proxyServer = new WslServer()
                .start();

//        WebSocketListener listener = mock(WebSocketListener.class);
        final List<ByteBuffer> bbList = new ArrayList<>();
        WebSocketListener listener = spy(new WebSocketListener() {
            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                bbList.add(bytes.asByteBuffer());
            }
        });
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        Request request = new Request.Builder()
                .url("ws://127.0.0.1:" + proxyServer.port() + "/")
                .build();
        WebSocket ws = client.newWebSocket(request, listener);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(listener, timeout(Duration.ofMillis(1000))).onOpen(eq(ws), response.capture());

        ControlMessage msg = new ControlMessage();
        msg.type = "request";
        msg.action = "connect";
        msg.address = "127.0.0.1";
        msg.port = EchoServer.PORT;
        ws.send(gson.toJson(msg));

        ArgumentCaptor<String> respTextMsg = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(Duration.ofMillis(1000)).times(2)).onMessage(eq(ws), respTextMsg.capture());
        msg = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("response", msg.type);
        assertEquals("success", msg.action);

        StringBuffer sb1 = new StringBuffer();
        StringBuffer sb2 = new StringBuffer();
        int total = 65536;
        for (int i = 0; i < total; i++) {
            sb1.append((char) ((i % 26) + 'a'));
            sb2.append((char) ((i % 26) + 'A'));
        }
        ws.send(ByteString.of(sb1.toString().getBytes()));
        ws.send(ByteString.of(sb2.toString().getBytes()));

//        ArgumentCaptor<ByteString> respByteMsg = ArgumentCaptor.forClass(ByteString.class);
//        verify(listener, after(Duration.ofMillis(1000)).times(4)).onMessage(eq(ws), respByteMsg.capture());
        verify(listener, after(Duration.ofMillis(3000)).atLeast(3)).onMessage(eq(ws), any(ByteString.class));

        int all = 0;
        for (ByteBuffer bb : bbList) {
            all += bb.remaining();
        }
        ByteBuffer respBuf = ByteBuffer.allocate(all);
        for (ByteBuffer bb : bbList) {
            respBuf.put(bb);
        }
        respBuf.rewind();
        assertEquals(total * 2, respBuf.remaining());

        int idx = 0; // The first byte
        assertEquals((idx % 26) + 'a', respBuf.get(idx));

        idx = sb1.length() - 1; // The last byte in lower case data
        assertEquals((idx % 26) + 'a', respBuf.get(idx));

        idx = sb1.length(); // The first byte in upper case data, should be 'A'
        assertEquals(((idx - sb1.length()) % 26) + 'A', respBuf.get(idx));

        idx = sb1.length() + sb2.length() - 1; // The last byte in upper case data, should be 'P'
        assertEquals(((idx - sb1.length()) % 26) + 'A', respBuf.get(idx));

        // Check random bytes
        Random rand = new Random();
        for (int i = 0; i < 9; i++) {
            idx = rand.nextInt(total * 2);
            if (idx >= 65536) {
                assertEquals("idx:" + idx, ((idx - 65536) % 26) + 'A', respBuf.get(idx));
            } else {
                assertEquals("idx:" + idx, (idx % 26) + 'a', respBuf.get(idx));
            }
        }

        proxyServer.stop();
        echoServer.stop();
    }

    @Test
    public void testProxyAuth() throws Exception {
        Gson gson = new Gson();
        MockWebServer httpServer = new MockWebServer();
        httpServer.start();

        UUID uuid = UUID.randomUUID();
        WslServer.Configuration conf = new WslServer.Configuration();
        conf.proxyUid = uuid.toString();
        WslServer server = new WslServer()
                .config(conf)
                .start();

        WebSocketListener listener = mock(WebSocketListener.class);
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        Request request = new Request.Builder()
                .url("ws://127.0.0.1:" + server.port() + "/")
                .build();
        WebSocket ws = client.newWebSocket(request, listener);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(listener, timeout(Duration.ofMillis(1000))).onOpen(eq(ws), response.capture());

        ArgumentCaptor<String> respTextMsg = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(Duration.ofMillis(1000))).onMessage(eq(ws), respTextMsg.capture());
        ControlMessage resp = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("hello", resp.type);
        assertEquals("hs256", resp.action);
        assertNotNull(resp.token);
        reset(listener);
        byte[] nonce = Base64.getDecoder().decode(resp.token);

        ControlMessage req = new ControlMessage();
        req.type = "request";
        req.action = "connect";
        req.address = "127.0.0.1";
        req.port = httpServer.getPort();
        ws.send(gson.toJson(req));

        verify(listener, timeout(Duration.ofMillis(1000))).onMessage(eq(ws), respTextMsg.capture());
        resp = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("response", resp.type);
        assertEquals("reject", resp.action);
        reset(listener);

        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(uuid.toString().getBytes(), "HmacSHA256"));
        hmac.update(nonce);
        hmac.update(req.address.getBytes());
        hmac.update((byte) req.port);
        req.token = Base64.getEncoder().encodeToString(hmac.doFinal());
        ws.send(gson.toJson(req));

        verify(listener, timeout(Duration.ofMillis(1000))).onMessage(eq(ws), respTextMsg.capture());
        resp = gson.fromJson(respTextMsg.getValue(), ControlMessage.class);
        assertEquals("response", resp.type);
        assertEquals("success", resp.action);
        reset(listener);

        // https://tools.ietf.org/html/rfc6455#section-7.4
        ws.close(1000, "Normal Closure");

        server.stop();
        httpServer.shutdown();
    }
}
