package com.rex.wsproxy;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class WsProxyLocalTest {

    // Test Socks5Server by URLConnection without auth
    @Test
    public void testAnonymous() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();

        WsProxyLocal proxy = new WsProxyLocal();
        proxy.start();

        URLConnection conn = new URL("http://127.0.0.1:" + server.getPort() + "/")
                .openConnection(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", proxy.port())));
        HttpURLConnection httpConn = (HttpURLConnection) conn;

        assertEquals(200, httpConn.getResponseCode());
        assertEquals("OK", httpConn.getResponseMessage());
        byte[] bytes = new byte[httpConn.getContentLength()];
        httpConn.getInputStream().read(bytes);
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(bytes))
                .toString());

        // Shutdown everything
        proxy.stop();
        server.close();
    }

    // Test Socks5Server by OkHttpClient without auth
    @Test
    public void testAnonymous2() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();

        WsProxyLocal proxy = new WsProxyLocal();
        proxy.start();


        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", proxy.port())))
                .build();

        Request request = new Request.Builder()
                .url(new URL("http://127.0.0.1:" + server.getPort()))
                .build();
        Response response = client
                .newCall(request)
                .execute();
        assertTrue(response.isSuccessful());
        assertEquals(200, response.code());
        assertEquals("OK", response.message());
        assertEquals("HelloWorld!", response.body().string());

        // Shutdown everything
        proxy.stop();
        server.close();
    }

    @Test
    public void testAuth() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("Hello World!"));
        server.start();

        WsProxyLocal.Configuration conf = new WsProxyLocal.Configuration();
        conf.authUser = "user";
        conf.authPassword = "password";
        WsProxyLocal proxy = new WsProxyLocal()
                .config(conf)
                .start();

        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", proxy.port())))
                .build();


        // Anonymous access should failed by java.net.SocketException("SOCKS : authentication failed")
        Request request = new Request.Builder()
                .url(new URL("http://127.0.0.1:" + server.getPort()))
                .build();
        try {
            client.newCall(request).execute();
            fail("Proxy auth required");
        } catch (SocketException ex) {
            assertEquals("SOCKS : authentication failed", ex.getMessage());
        }


        // Wrong password should also failed
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("user", "wrong password".toCharArray());
            }
        });
        try {
            request = new Request.Builder()
                    .url(new URL("http://127.0.0.1:" + server.getPort()))
                    .build();
            client.newCall(request)
                    .execute();
            fail("Proxy auth required");
        } catch (SocketException ex) {
            assertEquals("SOCKS : authentication failed", ex.getMessage());
        }


        // Correct password should success
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestingHost().equalsIgnoreCase("127.0.0.1") && getRequestingPort() == proxy.port()) {
                    return new PasswordAuthentication("user", "password".toCharArray());
                }
                return null;
            }
        });
        request = new Request.Builder()
                .url(new URL("http://127.0.0.1:" + server.getPort()))
                .build();
        Response response = client
                .newCall(request)
                .execute();
        assertTrue(response.isSuccessful());
        assertEquals(200, response.code());
        assertEquals("OK", response.message());
        assertEquals("Hello World!", response.body().string());


        // Shutdown everything
        proxy.stop();
        server.close();
    }

    @Test
    public void testSocksProxySlowStream() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!").throttleBody(3, 1, TimeUnit.SECONDS));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!").throttleBody(1, 1, TimeUnit.SECONDS));
        server.start();

        WsProxyLocal local = new WsProxyLocal()
                .start();

        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", local.port())))
                .build();

        // Send request, response slow but success
        Request request = new Request.Builder()
                .url(new URL("http://127.0.0.1:" + server.getPort()))
                .build();
        Response response = client
                .newCall(request)
                .execute();
        assertTrue(response.isSuccessful());
        assertEquals(200, response.code());
        assertEquals("OK", response.message());
        assertEquals("HelloWorld!", response.body().string());

        // Send request again
        response = client
                .newCall(request)
                .execute();
        assertTrue(response.isSuccessful());
        assertEquals(200, response.code());
        assertEquals("OK", response.message());
        assertEquals("HelloWorld!", response.body().string());

        // Shutdown everything
        local.stop();
        server.shutdown();
    }

    // Test works as socks proxy with large data transfer
    @Test
    public void testSocksProxyLargeData() throws Exception {
        StringBuffer sb = new StringBuffer();
        int total = 65536 * 2;
        for (int i = 0; i < total; i++) {
            sb.append((char) ((i % 26) + 'A'));
        }

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody(sb.toString()));
        server.start();

        WsProxyLocal local = new WsProxyLocal()
                .start();

        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", local.port())))
                .build();

        // Send request, response very large
        Request request = new Request.Builder()
                .url(new URL("http://127.0.0.1:" + server.getPort()))
                .build();
        Response response = client
                .newCall(request)
                .execute();
        assertTrue(response.isSuccessful());
        assertEquals(200, response.code());
        assertEquals("OK", response.message());

        byte[] body = response.body().bytes();
        assertEquals(total, body.length);

        int idx = 0; // The first byte
        assertEquals(((idx) % 26) + 'A', body[idx]);

        idx = body.length - 1; // The last byte
        assertEquals(((idx) % 26) + 'A', body[idx]);

        idx = body.length / 2; // The middle byte
        assertEquals(((idx) % 26) + 'A', body[idx]);

        Random rand = new Random();
        for (int i = 0; i < 9; i++) {
            idx = rand.nextInt(total);
            assertEquals((idx % 26) + 'A', body[idx]);
        }

        // Shutdown everything
        local.stop();
        server.shutdown();
    }

    // Test WsProxyLocal with real WsProxyServer by URLConnection without auth
    @Test
    public void testWsProxy() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();

        WsProxyServer remote = new WsProxyServer();
        remote.start();

        WsProxyLocal.Configuration localConfig = new WsProxyLocal.Configuration();
        localConfig.proxyUri = new URI("ws://127.0.0.1:" + remote.port() + "/ws");
        WsProxyLocal local = new WsProxyLocal();
        local.config(localConfig);
        local.start();

        URLConnection conn = new URL("http://127.0.0.1:" + server.getPort() + "/")
                .openConnection(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", local.port())));
        HttpURLConnection httpConn = (HttpURLConnection) conn;

        assertEquals(200, httpConn.getResponseCode());
        assertEquals("OK", httpConn.getResponseMessage());
        byte[] bytes = new byte[httpConn.getContentLength()];
        httpConn.getInputStream().read(bytes);
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(bytes))
                .toString());

        // Shutdown everything
        local.stop();
        remote.stop();
        server.close();
    }
}
