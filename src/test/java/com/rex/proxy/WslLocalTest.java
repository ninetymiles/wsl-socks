package com.rex.proxy;

import com.google.gson.Gson;
import com.rex.proxy.utils.EchoServer;
import com.rex.proxy.websocket.control.ControlMessage;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.*;
import java.net.Authenticator;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class WslLocalTest {

    private static final Logger sLogger = LoggerFactory.getLogger(WslLocalTest.class);

    private static final Gson sGson = new Gson();

    // Test Socks5Server by URLConnection without auth
    @Test
    public void testAnonymous() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();

        WslLocal proxy = new WslLocal()
                .config(new WslLocal.Configuration(0))
                .start();

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

        WslLocal proxy = new WslLocal()
                .config(new WslLocal.Configuration(0))
                .start();


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
    public void testHttpWebSocketBridge() throws Exception {
        WebSocketListener listener = mock(WebSocketListener.class);
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .withWebSocketUpgrade(listener)
                .setHeader("Sec-WebSocket-Protocol", "com.rex.websocket.protocol.proxy2")); // TODO: Share the const sub protocol between wsclient and wsserver
        server.start();
        HttpUrl url = server.url("/");
        sLogger.trace("Mock server: {}", url);

        WslLocal.Configuration cfg = new WslLocal.Configuration(0);
        cfg.bindProtocol = "http";
        cfg.proxyUri = new URI("ws://" + url.host()+ ":" + url.port());
        WslLocal proxy = new WslLocal()
                .config(cfg)
                .start();

        String targetHost = "some_host";
        int targetPort = 1234;

        SocketAddress proxyAddr = new InetSocketAddress("127.0.0.1", proxy.port());
        Socket client = new Socket();
        client.connect(proxyAddr, 3000);
        DataOutputStream out = new DataOutputStream(client.getOutputStream());
        DataInputStream in = new DataInputStream(client.getInputStream());
        out.write(("CONNECT " + targetHost + ":" + targetPort + " HTTP/1.1\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Host: " + targetHost + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Connection: keep-alive\r\n".getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // WslLocal connect to WslServer
        ArgumentCaptor<WebSocket> ws = ArgumentCaptor.forClass(WebSocket.class);
        verify(listener, timeout(3000)).onOpen(ws.capture(), any(Response.class));

        // WslServer handshake without auth
        ControlMessage msg = new ControlMessage();
        msg.type = "hello";
        ws.getValue().send(sGson.toJson(msg));

        // WslLocal request tunnel to target address
        ArgumentCaptor<String> ack = ArgumentCaptor.forClass(String.class);
        verify(listener, timeout(3000)).onMessage(any(), ack.capture());
        msg = sGson.fromJson(ack.getAllValues().get(0), ControlMessage.class);
        assertEquals("request", msg.type);
        assertEquals("connect", msg.action);
        assertEquals(targetHost, msg.address);
        assertEquals(Integer.valueOf(targetPort), msg.port);

        // WslServer accept the request to allow WslLocal set up the bridge
        msg = new ControlMessage();
        msg.type = "response";
        msg.action = "success";
        ws.getValue().send(sGson.toJson(msg));

        // WslLocal ack with tunnel is ready
        String[] status = in.readLine().split(" "); // HTTP/1.1 200 Connection established\r\n
        assertEquals("200", status[1]);
        in.readLine(); // \r\n


        // WslLocal forward all the binary data as BinaryWebSocketFrame
        out.write("Hello".getBytes(StandardCharsets.UTF_8));
        verify(listener, timeout(3000)).onMessage(any(), eq(ByteString.encodeUtf8("Hello")));

        // WslLocal forward BinaryWebSocketFrame back to binary data
        ws.getValue().send(ByteString.encodeUtf8("World!\r\n"));
        assertEquals("World!", in.readLine());

        // Close everything
        client.close();
        proxy.stop();
        server.close();
    }

    @Test
    public void testHttpDirect() throws Exception {
        EchoServer echo = new EchoServer();
        WslLocal proxy = null;
        Socket client = null;

        try {
            echo.start();

            WslLocal.Configuration cfg = new WslLocal.Configuration(0);
            cfg.bindProtocol = "http";
            proxy = new WslLocal()
                    .config(cfg)
                    .start();

            client = new Socket();
            client.connect(new InetSocketAddress("127.0.0.1", proxy.port()), 3000);
            client.setSoTimeout(3000);

            DataOutputStream out = new DataOutputStream(client.getOutputStream());
            DataInputStream in = new DataInputStream(client.getInputStream());

            out.write(("CONNECT 127.0.0.1:" + echo.port() + " HTTP/1.1\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write("Host: 127.0.0.1\r\n".getBytes(StandardCharsets.UTF_8));
            out.write("Connection: keep-alive\r\n".getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String statusLine = in.readLine();
            assertNotNull(statusLine);
            String[] status = statusLine.split(" ");
            assertEquals("200", status[1]);

            String line;
            do {
                line = in.readLine();
            } while (line != null && !line.isEmpty());

            out.write("HelloProxy\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertEquals("HelloProxy", in.readLine());
        } finally {
            if (client != null) {
                client.close();
            }
            if (proxy != null) {
                proxy.stop();
            }
            echo.stop();
        }
    }

    @Ignore("OkHttpClient will use HTTP/1.0 GET method to connect proxy, not supported yet")
    @Test
    public void testHttpDirect2() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody("HelloWorld!"));
        server.start();
        sLogger.trace("Mock server: {}", server.url("/"));

        WslLocal.Configuration cfg = new WslLocal.Configuration("127.0.0.1", 0);
        cfg.bindProtocol = "http";
        WslLocal proxy = new WslLocal()
                .config(cfg)
                .start();

        SocketAddress proxyAddr = new InetSocketAddress("127.0.0.1", proxy.port());
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP, proxyAddr))
                .build();
        Request request = new Request.Builder()
                .url(server.url("/"))
                .build();
        Response response = client
                .newCall(request)
                .execute();
        assertNotNull(response);
        assertTrue(response.isSuccessful());
        assertEquals(200, response.code());
        assertEquals("OK", response.message());
        assertNotNull(response.body());
        assertEquals("HelloWorld!", response.body().string());

        // Shutdown everything
        response.body().close();
        response.close();
        server.close();
        proxy.stop();
    }

    @Test
    public void testAuth() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("Hello World!"));
        server.start();

        WslLocal.Configuration conf = new WslLocal.Configuration();
        conf.bindPort = 0; // auto select port
        conf.authUser = "user";
        conf.authPassword = "password";
        WslLocal proxy = new WslLocal()
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
    public void testServerClose() throws Exception {
        EchoServer server = new EchoServer()
                .port(8007)
                .start();

        WslLocal proxy = new WslLocal()
                .config(new WslLocal.Configuration(0))
                .start();

        Socket client = new Socket();
        client.setSoTimeout(5000); // milliseconds 5s
        client.connect(new InetSocketAddress("127.0.0.1", proxy.port()));

        DataOutputStream output = new DataOutputStream(client.getOutputStream());
        DataInputStream input = new DataInputStream(client.getInputStream());
        byte[] buffer = new byte[1024];

        // Socks5InitialRequest
        output.write(new byte[] { 0x05, 0x02, 0x00, 0x02 });

        // Socks5InitialResponse NO_AUTH
        assertEquals(2, input.read(buffer, 0, 2)); // 05 00

        // Socks5CommandRequest CONNECT 127.0.0.1:8007
        output.write(new byte[] { 0x05, 0x01, 0x00, 0x01, 0x7f, 0x00, 0x00, 0x01, (byte) 0x1f, 0x47 });

        // Socks5CommandResponse SUCCESS // TODO: Verify the ack
        assertEquals(10, input.read(buffer, 0, 10)); // 05 00 00 01 00 00 00 00 00 00

        // Proxy some data
        output.write("HelloWorld!".getBytes());
        assertEquals(11, input.read(buffer, 0, 11)); // HelloWorld!


        // WebServer close the socket
        server.stop();
        assertEquals(-1, input.read(buffer, 0, 11));

        // Shutdown everything
        proxy.stop();
    }

    @Test
    public void testClientClose() throws Exception {
        EchoServer.ChildListener listener = mock(EchoServer.ChildListener.class);
        EchoServer server = new EchoServer()
                .setChildListener(listener)
                .port(8007)
                .start();

        WslLocal proxy = new WslLocal()
                .config(new WslLocal.Configuration(0))
                .start();

        Socket client = new Socket();
        client.setSoTimeout(5000); // milliseconds 5s
        client.connect(new InetSocketAddress("127.0.0.1", proxy.port()));

        DataOutputStream output = new DataOutputStream(client.getOutputStream());
        DataInputStream input = new DataInputStream(client.getInputStream());
        byte[] buffer = new byte[1024];

        // Socks5InitialRequest
        output.write(new byte[] { 0x05, 0x02, 0x00, 0x02 });

        // Socks5InitialResponse NO_AUTH
        assertEquals(2, input.read(buffer, 0, 2)); // 05 00

        // Socks5CommandRequest CONNECT 127.0.0.1:8007
        output.write(new byte[] { 0x05, 0x01, 0x00, 0x01, 0x7f, 0x00, 0x00, 0x01, (byte) 0x1f, 0x47 });

        // Socks5CommandResponse SUCCESS // TODO: Verify the ack
        assertEquals(10, input.read(buffer, 0, 10)); // 05 00 00 01 00 00 00 00 00 00

        // Proxy some data
        output.write("HelloWorld!".getBytes());
        assertEquals(11, input.read(buffer, 0, 11)); // HelloWorld!


        // Client force close the socket
        client.close();
        verify(listener, timeout(Duration.ofSeconds(2).toMillis())).onClosed(any());

        // Shutdown everything
        server.stop();
        proxy.stop();
    }

    @Test
    public void testBind() throws Exception {
        WslLocal proxy = new WslLocal()
                .config(new WslLocal.Configuration(0))
                .start();

        Socket client = new Socket();
        client.setSoTimeout(5000); // milliseconds 5s
        client.connect(new InetSocketAddress("127.0.0.1", proxy.port()));

        DataOutputStream clientOutput = new DataOutputStream(client.getOutputStream());
        DataInputStream clientInput = new DataInputStream(client.getInputStream());
        byte[] buffer = new byte[1024];

        // Socks5InitialRequest
        clientOutput.write(new byte[] { 0x05, 0x02, 0x00, 0x02 });

        // Socks5InitialResponse NO_AUTH { 0x05 0x00 }
        assertEquals(0x05, clientInput.readUnsignedByte()); // VER(1): socks5
        assertEquals(0x00, clientInput.readUnsignedByte()); // REP(1): succeeded

        // Socks5CommandRequest BIND 0.0.0.0:0
        clientOutput.write(new byte[] { 0x05, 0x02, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });

        // Socks5CommandResponse SUCCESS with local address
        assertEquals(0x05, clientInput.readUnsignedByte()); // VER(1): socks5
        assertEquals(0x00, clientInput.readUnsignedByte()); // REP(1): succeeded
        assertEquals(0x00, clientInput.readUnsignedByte()); // RSV(1)

        InetAddress inetAddr = null;
        int type = clientInput.readUnsignedByte(); // ATYP(1)
        if (Socks5AddressType.IPv4.byteValue() == type) {
            byte[] addr = new byte[4];
            clientInput.read(addr); // BND.ADDR: 0.0.0.0
            inetAddr = InetAddress.getByAddress(addr);
        } else if (Socks5AddressType.IPv6.byteValue() == type) {
            byte[] addr6 = new byte[16];
            clientInput.read(addr6); // BND.ADDR: 0:0:0:0:0:0:0:0
            inetAddr = Inet6Address.getByAddress(addr6);
        }
        int port = clientInput.readUnsignedShort();

        Socket reverse = new Socket();
        reverse.setSoTimeout(5000); // milliseconds 5s
        reverse.connect(new InetSocketAddress(inetAddr, port));
        DataOutputStream reverseOutput = new DataOutputStream(reverse.getOutputStream());
        DataInputStream reverseInput = new DataInputStream(reverse.getInputStream());

        // Socks5CommandResponse SUCCESS with remote address
        assertEquals(0x05, clientInput.readUnsignedByte()); // VER(1): socks5
        assertEquals(0x00, clientInput.readUnsignedByte()); // REP(1): succeeded
        assertEquals(0x00, clientInput.readUnsignedByte()); // RSV(1)

        type = clientInput.readUnsignedByte(); // ATYP(1)
        if (Socks5AddressType.IPv4.byteValue() == type) {
            byte[] addr = new byte[4];
            clientInput.read(addr); // BND.ADDR: 0.0.0.0
            inetAddr = Inet6Address.getByAddress(addr);
        } else if (Socks5AddressType.IPv6.byteValue() == type) {
            byte[] addr6 = new byte[16];
            clientInput.read(addr6); // BND.ADDR: 0:0:0:0:0:0:0:0
            inetAddr = Inet6Address.getByAddress(addr6);
        }
        port = clientInput.readUnsignedShort();

        assertEquals(reverse.getLocalAddress().getHostAddress(), inetAddr.getHostAddress());
        assertEquals(reverse.getLocalPort(), port);

        // Forward some data
        clientOutput.write("HelloWorld!".getBytes());
        assertEquals(11, reverseInput.read(buffer));
        assertEquals("HelloWorld!", StandardCharsets.UTF_8.newDecoder()
                .decode(ByteBuffer.wrap(buffer, 0, 11))
                .toString());

        // Backward some data
        reverseOutput.write("ABCDEF".getBytes());
        assertEquals(6, clientInput.read(buffer));
        assertEquals("ABCDEF", StandardCharsets.UTF_8.newDecoder()
                .decode(ByteBuffer.wrap(buffer, 0, 6))
                .toString());

        // Shutdown everything
        client.close();
        reverse.close();
        proxy.stop();
    }

    @Test
    public void testSocksProxySlowStream() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!").throttleBody(3, 1, TimeUnit.SECONDS));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!").throttleBody(1, 1, TimeUnit.SECONDS));
        server.start();

        WslLocal local = new WslLocal()
                .config(new WslLocal.Configuration(0))
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

        WslLocal local = new WslLocal()
                .config(new WslLocal.Configuration(0))
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

        WslServer remote = new WslServer();
        remote.start();

        WslLocal.Configuration localConfig = new WslLocal.Configuration();
        localConfig.bindPort = 0; // auto select port
        localConfig.proxyUri = new URI("ws://127.0.0.1:" + remote.port() + "/");
        WslLocal local = new WslLocal()
                .config(localConfig)
                .start();

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

    // Test WsProxyLocal connect TLS WsProxyServer with self-signed certificate
    @Test
    public void testWssProxyIgnoreCert() throws Exception {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("HelloWorld!"));
        server.start();

        WslServer.Configuration remoteConfig = new WslServer.Configuration("127.0.0.1", 1234,
                ClassLoader.getSystemResource("test.cert.pem").getFile(),
                ClassLoader.getSystemResource("test.key.p8.pem").getFile());
        WslServer remote = new WslServer()
                .config(remoteConfig)
                .start();

        WslLocal.Configuration localConfig = new WslLocal.Configuration();
        localConfig.bindPort = 0; // auto select port
        localConfig.proxyUri = new URI("wss://127.0.0.1:" + remote.port() + "/");
        localConfig.proxyCertVerify = false;
        WslLocal local = new WslLocal()
                .config(localConfig)
                .start();

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

    @Test
    public void testSocketCallback() throws Exception {
        WslLocal.SocketCallback cb = spy(new WslLocal.SocketCallback() {
            @Override
            public void onConnect(Socket s) {
                assertFalse(s.isConnected());
            }
        });

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(200));
        server.start();

        WslLocal.Configuration conf = new WslLocal.Configuration();
        conf.bindPort = 0; // Auto select
        conf.callback = cb;
        WslLocal proxy = new WslLocal()
                .config(conf)
                .start();

        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", proxy.port())))
                .build();

        Request request = new Request.Builder()
                .url(new URL("http://127.0.0.1:" + server.getPort()))
                .build();
        Response response = client
                .newCall(request)
                .execute();

        ArgumentCaptor<Socket> socket = ArgumentCaptor.forClass(Socket.class);
        verify(cb, timeout(Duration.ofSeconds(1).toMillis())).onConnect(socket.capture());

        assertTrue(response.isSuccessful());
        assertEquals(200, response.code());

        InetSocketAddress addr = (InetSocketAddress) socket.getValue().getRemoteSocketAddress();
        assertEquals(server.getPort(), addr.getPort());
        assertEquals("127.0.0.1", addr.getAddress().getHostAddress());

        // Shutdown everything
        proxy.stop();
        server.close();
    }
}
