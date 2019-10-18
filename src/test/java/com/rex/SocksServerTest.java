package com.rex;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class SocksServerTest {

    // Test Socks5Server by URLConnection without auth
    @Test
    public void testAnonymous() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();

        SocksServer proxy = new SocksServer();
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

        SocksServer proxy = new SocksServer();
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

        SocksServer.Configuration conf = new SocksServer.Configuration();
        conf.authUser = "user";
        conf.authPassword = "password";
        SocksServer proxy = new SocksServer()
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
}
