package com.rex.socks;

import com.rex.Socks5Server;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import java.net.*;

import static org.junit.Assert.assertEquals;

public class SocksServerTest {

    @Test
    public void testConnect() throws Exception {
        final MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(404));
        server.start();

        Socks5Server proxy = new Socks5Server();
        proxy.start();

        URLConnection conn = new URL("http://localhost:" + server.getPort() + "/")
                .openConnection(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", proxy.port())));
        HttpURLConnection httpConn = (HttpURLConnection) conn;

        assertEquals(404, httpConn.getResponseCode());
    }
}
