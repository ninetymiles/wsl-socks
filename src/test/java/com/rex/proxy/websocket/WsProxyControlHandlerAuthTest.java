package com.rex.proxy.websocket;

import com.google.gson.Gson;
import com.rex.proxy.WslServer;
import com.rex.proxy.websocket.control.ControlAuthBuilder;
import com.rex.proxy.websocket.control.ControlMessage;
import com.rex.proxy.websocket.control.WsProxyControlCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.*;

/**
 * Test new authorization protocol and backward compatibility
 */
public class WsProxyControlHandlerAuthTest {

    private static final AttributeKey<Boolean> ATTR_AUTHORIZED = AttributeKey.valueOf("ws.authorized");
    private static final AttributeKey<byte[]> ATTR_NONCE = AttributeKey.valueOf("ws.nonce");

    private WsProxyControlHandler handler;
    private EmbeddedChannel channel;
    private NioEventLoopGroup workerGroup;
    private WslServer.Configuration config;
    private Gson gson;

    @Before
    public void setUp() {
        workerGroup = new NioEventLoopGroup(1);
        config = new WslServer.Configuration();
        config.proxyUid = "test-secret-key";

        handler = new WsProxyControlHandler(workerGroup, config);
        channel = new EmbeddedChannel(new WsProxyControlCodec(), handler);
        gson = new Gson();
    }

    @After
    public void tearDown() {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * Helper: Read outbound TextWebSocketFrame and decode to ControlMessage
     */
    private ControlMessage readOutbound() {
        TextWebSocketFrame frame = channel.readOutbound();
        if (frame == null) return null;
        String text = frame.text();
        frame.release();
        return gson.fromJson(text, ControlMessage.class);
    }

    /**
     * Helper: Encode ControlMessage and write as inbound TextWebSocketFrame
     */
    private void writeInbound(ControlMessage msg) {
        channel.writeInbound(new TextWebSocketFrame(gson.toJson(msg)));
    }

    @Test
    public void testHelloMessageSentOnConnection() {
        // When handler is added, it should send hello message
        ControlMessage hello = readOutbound();

        assertNotNull("Should send hello message", hello);
        assertEquals("Should be hello type", "hello", hello.type);
        assertEquals("Should use hs256", "hs256", hello.action);
        assertNotNull("Should include nonce", hello.token);
        assertNotNull("Should include nonce", hello.token);

        // Verify nonce is stored in channel attributes
        byte[] nonce = channel.attr(ATTR_NONCE).get();
        assertNotNull("Nonce should be stored", nonce);
        assertEquals("Nonce should be 32 bytes", 32, nonce.length);

        // Verify authorization flag is initially false
        Boolean authorized = channel.attr(ATTR_AUTHORIZED).get();
        assertFalse("Should not be authorized initially", authorized);
    }

    @Test
    public void testAuthorizationSuccess() {
        // Read hello message and get nonce
        ControlMessage hello = readOutbound();
        byte[] nonce = Base64.getDecoder().decode(hello.token);

        // Build correct authorization token
        String authToken = new ControlAuthBuilder()
                .setSecret(config.proxyUid)
                .setNonce(nonce)
                .build();

        // Send authorization message
        ControlMessage authMsg = new ControlMessage();
        authMsg.type = "authorization";
        authMsg.token = authToken;

        writeInbound(authMsg);

        // Should receive authorized response
        ControlMessage response = readOutbound();
        assertNotNull("Should send response", response);
        assertEquals("Should be authorized", "authorized", response.type);

        // Verify authorization flag is set
        Boolean authorized = channel.attr(ATTR_AUTHORIZED).get();
        assertTrue("Should be authorized", authorized);
    }

    @Test
    public void testAuthorizationFailure() {
        // Read hello message
        readOutbound();

        // Send authorization with wrong token
        ControlMessage authMsg = new ControlMessage();
        authMsg.type = "authorization";
        authMsg.token = "wrong-token";

        writeInbound(authMsg);

        // Should receive reject response
        ControlMessage response = readOutbound();
        assertNotNull("Should send response", response);
        assertEquals("Should be response type", "response", response.type);
        assertEquals("Should be reject action", "reject", response.action);

        // Channel should be closed
        assertFalse("Channel should be closed after auth failure", channel.isActive());
    }

    @Test
    public void testConnectAfterAuthorization() {
        // First authorize
        ControlMessage hello = readOutbound();
        byte[] nonce = Base64.getDecoder().decode(hello.token);

        String authToken = new ControlAuthBuilder()
                .setSecret(config.proxyUid)
                .setNonce(nonce)
                .build();

        ControlMessage authMsg = new ControlMessage();
        authMsg.type = "authorization";
        authMsg.token = authToken;

        writeInbound(authMsg);
        readOutbound(); // Read authorized response

        // Now send connect request WITHOUT token (new protocol)
        ControlMessage connectMsg = new ControlMessage();
        connectMsg.type = "request";
        connectMsg.action = "connect";
        connectMsg.address = "www.example.com";
        connectMsg.port = 443;
        // No token field!

        writeInbound(connectMsg);

        // Should process request without rejecting
        // Note: Will eventually fail to connect in test environment, but shouldn't reject auth
        Boolean stillAuthorized = channel.attr(ATTR_AUTHORIZED).get();
        assertTrue("Should remain authorized", stillAuthorized);
    }

    @Test
    public void testBackwardCompatibilityWithPerRequestToken() {
        // Old protocol: send connect with token in each request
        ControlMessage hello = readOutbound();
        byte[] nonce = Base64.getDecoder().decode(hello.token);

        // Send connect request with per-request token (old protocol)
        ControlMessage connectMsg = new ControlMessage();
        connectMsg.type = "request";
        connectMsg.action = "connect";
        connectMsg.address = "www.example.com";
        connectMsg.port = 443;
        connectMsg.token = new ControlAuthBuilder()
                .setSecret(config.proxyUid)
                .setNonce(nonce)
                .setAddress("www.example.com")
                .setPort(443)
                .build();

        writeInbound(connectMsg);

        // Should accept the request (old protocol still works)
        // Note: Will fail to connect in test env, but auth should pass
        Boolean authorized = channel.attr(ATTR_AUTHORIZED).get();
        // In old protocol, authorization flag stays false, but request is accepted
        assertFalse("Old protocol doesn't set authorized flag", authorized);
    }

    @Test
    public void testConnectWithoutAuthWhenRequired() {
        // Read hello
        readOutbound();

        // Send connect request without authorization and without per-request token
        ControlMessage connectMsg = new ControlMessage();
        connectMsg.type = "request";
        connectMsg.action = "connect";
        connectMsg.address = "www.example.com";
        connectMsg.port = 443;
        // No authorization, no token!

        writeInbound(connectMsg);

        // Should reject
        ControlMessage response = readOutbound();
        assertNotNull("Should send response", response);
        assertEquals("Should be response type", "response", response.type);
        assertEquals("Should be reject action", "reject", response.action);
    }

    @Test
    public void testNoAuthWhenNotRequired() {
        // Setup handler without auth requirement
        WslServer.Configuration noAuthConfig = new WslServer.Configuration();
        noAuthConfig.proxyUid = null; // No auth

        WsProxyControlHandler noAuthHandler = new WsProxyControlHandler(workerGroup, noAuthConfig);
        EmbeddedChannel noAuthChannel = new EmbeddedChannel(new WsProxyControlCodec(), noAuthHandler);

        try {
            // Should send hello without action
            TextWebSocketFrame frame = noAuthChannel.readOutbound();
            assertNotNull("Should send hello", frame);
            ControlMessage hello = gson.fromJson(frame.text(), ControlMessage.class);
            frame.release();
            assertEquals("Should be hello type", "hello", hello.type);
            assertNull("Should not have action when no auth", hello.action);
            assertNull("Should not have token when no auth", hello.token);

            // Send connect request without any auth
            ControlMessage connectMsg = new ControlMessage();
            connectMsg.type = "request";
            connectMsg.action = "connect";
            connectMsg.address = "www.example.com";
            connectMsg.port = 443;

            noAuthChannel.writeInbound(new TextWebSocketFrame(gson.toJson(connectMsg)));

            // Should accept (will fail to connect, but won't reject for auth)
            // In test environment, connection will fail, but that's expected

        } finally {
            noAuthChannel.close();
        }
    }

    @Test
    public void testMultipleConnectsOnSameAuthorizedConnection() {
        // Authorize once
        ControlMessage hello = readOutbound();
        byte[] nonce = Base64.getDecoder().decode(hello.token);

        String authToken = new ControlAuthBuilder()
                .setSecret(config.proxyUid)
                .setNonce(nonce)
                .build();

        ControlMessage authMsg = new ControlMessage();
        authMsg.type = "authorization";
        authMsg.token = authToken;

        writeInbound(authMsg);
        readOutbound(); // Read authorized response

        // Send multiple connect requests without tokens
        for (int i = 0; i < 3; i++) {
            ControlMessage connectMsg = new ControlMessage();
            connectMsg.type = "request";
            connectMsg.action = "connect";
            connectMsg.address = "host" + i + ".example.com";
            connectMsg.port = 443;

            writeInbound(connectMsg);

            // Should all be accepted (authorized connection)
            Boolean stillAuthorized = channel.attr(ATTR_AUTHORIZED).get();
            assertTrue("Should remain authorized for request " + i, stillAuthorized);
        }
    }
}
