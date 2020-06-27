package com.rex.proxy;

import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.*;
import java.time.Duration;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class WslTest {

    @Test
    public void testServerConfig() throws Exception {
        // getClass().getResourceAsStream() point to build/classes/java/test/
        // ClassLoader.getSystemResourceAsStream() point to build/resources/test/

        Properties config = new Properties();
        config.load(ClassLoader.getSystemResourceAsStream("config_server.properties"));

        WslServer server = new Wsl().server(config);

        WebSocketListener listener = mock(WebSocketListener.class);
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        Request request = new Request.Builder()
                .url("ws://127.0.0.1:" + server.port() + "/")
                .build();
        WebSocket ws = client.newWebSocket(request, listener);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(listener, timeout(Duration.ofSeconds(1).toMillis())).onOpen(eq(ws), response.capture());

        server.stop();
    }

    @Test
    public void testClientConfig() throws Exception {
        Properties config = new Properties();
        config.load(ClassLoader.getSystemResourceAsStream("config_local.properties"));

        MockWebServer webServer = new MockWebServer();
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        webServer.start();

        WslLocal local = new Wsl().local(config);

        URLConnection conn = new URL("http://127.0.0.1:" + webServer.getPort() + "/")
                .openConnection(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", local.port())));
        HttpURLConnection connHttp = (HttpURLConnection) conn;
        assertEquals(200, connHttp.getResponseCode());

        local.stop();
    }
}
