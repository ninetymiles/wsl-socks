package com.rex.websocket;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class WsServerTest {

    @Test
    public void testConfigPort() throws Exception {
        final int port = 1234;
        WsServer server = new WsServer()
                .config(new WsServer.Configuration("127.0.0.1", port))
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
        WsServer server = new WsServer()
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
        WsServer server = new WsServer()
                .config(new WsServer.Configuration("127.0.0.1", 1234,
                        ClassLoader.getSystemResource("test.cert.pem").getFile(),
                        ClassLoader.getSystemResource("test.key.p8.pem").getFile()))
                .start();

        URLConnection conn = new URL("https://127.0.0.1:" + server.port() + "/")
                .openConnection();


        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null,
                new X509TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }
                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    }
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                } },
                null);

        HttpsURLConnection connHttps = (HttpsURLConnection) conn;
        connHttps.setSSLSocketFactory(ctx.getSocketFactory());
        connHttps.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });

        assertEquals(404, connHttps.getResponseCode());

        server.stop();
    }

    @Test
    public void testSSLWithEncryptedKey() throws Exception {
        WsServer server = new WsServer()
                .config(new WsServer.Configuration("127.0.0.1", 1234,
                        ClassLoader.getSystemResource("test.cert.pem").getFile(),
                        ClassLoader.getSystemResource("test.key.p8.encrypted.pem").getFile(),
                        "TestOnly"))
                .start();

        server.stop();
    }

    @Test
    public void testAcceptAndClose() throws Exception {
        WsServer.Callback cb = mock(WsServer.Callback.class);
        WsServer server = new WsServer()
                .setCallback(cb)
                .start();

        // Stop client, should trigger server callback onRemoved()
        URI uri = new URI("ws://localhost:" + server.port() + "/ws");
        WsClient client = new WsClient()
                .setSubProtocol(WsServerPathInterceptor.SUBPROTOCOL)
                .start(uri);

        ArgumentCaptor<WsConnection> conn = ArgumentCaptor.forClass(WsConnection.class);
        verify(cb, timeout(5000)).onAdded(eq(server), conn.capture());

        client.stop();
        verify(cb, timeout(5000)).onRemoved(eq(server), eq(conn.getValue()));


        // Stop server, should also trigger callback onRemoved()
        reset(cb);
        client.start(uri);
        verify(cb, timeout(5000)).onAdded(eq(server), conn.capture());

        server.stop();
        verify(cb, timeout(5000)).onRemoved(eq(server), eq(conn.getValue()));
    }

    @Test
    public void testSend() throws Exception {
        WsServer.Callback cb = mock(WsServer.Callback.class);
        WsServer server = new WsServer()
                .setCallback(cb)
                .start();

        WsClient client = new WsClient()
                .setSubProtocol(WsServerPathInterceptor.SUBPROTOCOL)
                .start(new URI("ws://localhost:"  + server.port() + "/ws"));

        ArgumentCaptor<WsConnection> conn = ArgumentCaptor.forClass(WsConnection.class);
        verify(cb, timeout(5000)).onAdded(eq(server), conn.capture());

        client.send(ByteBuffer.wrap("HelloWorld!".getBytes()));
        ArgumentCaptor<ByteBuffer> data = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(cb, timeout(1000)).onReceived(eq(server), eq(conn.getValue()), data.capture());
        assertEquals("HelloWorld!", StandardCharsets.UTF_8
                .newDecoder()
                .decode(data.getValue())
                .toString());

        server.stop();
    }
}
