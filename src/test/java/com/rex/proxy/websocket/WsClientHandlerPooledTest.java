package com.rex.proxy.websocket;

import com.google.gson.Gson;
import com.rex.proxy.WslLocal;
import com.rex.proxy.websocket.control.ControlMessage;
import com.rex.proxy.websocket.control.WsProxyControlCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Test WsClientHandler in pooled connection mode
 */
public class WsClientHandlerPooledTest {

    private WslLocal.Configuration config;
    private EmbeddedChannel wsChannel;
    private EmbeddedChannel localChannel;
    private Gson gson;

    @Before
    public void setUp() throws Exception {
        config = new WslLocal.Configuration();
        config.proxyUri = new URI("ws://localhost:9777/proxy");
        config.proxyUid = "test-secret";

        wsChannel = new EmbeddedChannel();
        localChannel = new EmbeddedChannel();
        gson = new Gson();
    }

    @After
    public void tearDown() {
        if (wsChannel != null && wsChannel.isOpen()) {
            wsChannel.close();
        }
        if (localChannel != null && localChannel.isOpen()) {
            localChannel.close();
        }
    }

    /**
     * Helper: Read outbound TextWebSocketFrame and decode to ControlMessage
     */
    private ControlMessage readOutbound(EmbeddedChannel channel) {
        TextWebSocketFrame frame = channel.readOutbound();
        if (frame == null) return null;
        String text = frame.text();
        frame.release();
        return gson.fromJson(text, ControlMessage.class);
    }

    /**
     * Helper: Encode ControlMessage and write as inbound TextWebSocketFrame
     */
    private void writeInbound(EmbeddedChannel channel, ControlMessage msg) {
        channel.writeInbound(new TextWebSocketFrame(gson.toJson(msg)));
    }

    @Test
    public void testPooledModeSkipsHello() {
        // Create handler in pooled mode
        WsClientHandler handler = new WsClientHandler(
                localChannel,
                "www.example.com",
                443,
                config.proxyUid,
                true  // isPooled = true
        );

        EmbeddedChannel testChannel = new EmbeddedChannel(new WsProxyControlCodec(), handler);

        // In pooled mode, should immediately send connect request
        ControlMessage msg = readOutbound(testChannel);
        assertNotNull("Should send message", msg);
        assertEquals("Should be request type", "request", msg.type);
        assertEquals("Should be connect action", "connect", msg.action);
        assertEquals("Should have correct address", "www.example.com", msg.address);
        assertEquals("Should have correct port", Integer.valueOf(443), msg.port);

        testChannel.close();
    }

    @Test
    public void testNonPooledModeWaitsForHello() {
        // Create handler in non-pooled mode
        WsClientHandler handler = new WsClientHandler(
                localChannel,
                "www.example.com",
                443,
                config.proxyUid,
                false  // isPooled = false
        );

        EmbeddedChannel testChannel = new EmbeddedChannel(new WsProxyControlCodec(), handler);

        // Should not send connect immediately (waits for hello)
        ControlMessage msg = readOutbound(testChannel);
        assertNull("Should not send message before hello", msg);

        testChannel.close();
    }

    @Test
    public void testPooledModeWithoutAuthSendsConnectDirectly() {
        // Config without auth
        WslLocal.Configuration noAuthConfig = new WslLocal.Configuration();
        try {
            noAuthConfig.proxyUri = new URI("ws://localhost:9777/proxy");
            noAuthConfig.proxyUid = null; // No auth
        } catch (Exception e) {
            fail("URI creation failed");
        }

        WsClientHandler handler = new WsClientHandler(
                localChannel,
                "www.example.com",
                443,
                noAuthConfig.proxyUid,
                true  // isPooled = true
        );

        EmbeddedChannel testChannel = new EmbeddedChannel(new WsProxyControlCodec(), handler);

        // Should send connect request without token
        ControlMessage msg = readOutbound(testChannel);
        assertNotNull("Should send connect", msg);
        assertEquals("Should be connect request", "connect", msg.action);
        assertNull("Should not have token when no auth", msg.token);

        testChannel.close();
    }

    @Test
    public void testPooledModeReceivesAuthorizedMessage() {
        WsClientHandler handler = new WsClientHandler(
                localChannel,
                "www.example.com",
                443,
                config.proxyUid,
                true  // isPooled
        );

        EmbeddedChannel testChannel = new EmbeddedChannel(new WsProxyControlCodec(), handler);

        // Read the connect request that was sent
        readOutbound(testChannel);

        // Simulate receiving "authorized" message from server
        ControlMessage authorized = new ControlMessage();
        authorized.type = "authorized";

        writeInbound(testChannel,  authorized);

        // Handler should process it without error
        // Channel should still be active
        assertTrue("Channel should remain active", testChannel.isActive());

        testChannel.close();
    }

    @Test
    public void testResponseMessageHandling() {
        WsClientHandler handler = new WsClientHandler(
                localChannel,
                "www.example.com",
                443,
                config.proxyUid,
                true  // isPooled
        );

        EmbeddedChannel testChannel = new EmbeddedChannel(new WsProxyControlCodec(), handler);
        readOutbound(testChannel); // Clear connect message

        // Simulate server response
        ControlMessage response = new ControlMessage();
        response.type = "response";
        response.action = "success"; // Use "success" as per ControlMessage protocol

        writeInbound(testChannel,  response);

        // Handler should process response
        assertTrue("Channel should remain active on success", testChannel.isActive());

        testChannel.close();
    }

    @Test
    public void testRejectResponseClosesConnection() {
        WsClientHandler handler = new WsClientHandler(
                localChannel,
                "www.example.com",
                443,
                config.proxyUid,
                true  // isPooled
        );

        // Add a handler to capture user events - must be AFTER the handler in the pipeline
        final AtomicReference<Object> capturedEvent = new AtomicReference<>();
        ChannelInboundHandlerAdapter eventCapture = new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                capturedEvent.set(evt);
                ctx.fireUserEventTriggered(evt); // Propagate the event
            }
        };

        EmbeddedChannel testChannel = new EmbeddedChannel(
                new WsProxyControlCodec(),
                handler,
                eventCapture  // Place after handler to catch events fired by handler
        );

        readOutbound(testChannel); // Clear connect message

        // Simulate server reject response
        ControlMessage response = new ControlMessage();
        response.type = "response";
        response.action = "reject";

        writeInbound(testChannel,  response);
        testChannel.runPendingTasks();

        // Debug: print captured event
        System.out.println("Captured event: " + capturedEvent.get());

        // Verify REMOTE_FAILED event was triggered
        assertNotNull("Should capture an event", capturedEvent.get());
        assertEquals("Should trigger REMOTE_FAILED event",
                     WslLocal.RemoteStateEvent.REMOTE_FAILED,
                     capturedEvent.get());

        // Verify an empty buffer was written (before close)
        ByteBuf buf = testChannel.readOutbound();
        assertNotNull("Should write empty buffer before close", buf);
        assertEquals("Buffer should be empty", 0, buf.readableBytes());
        buf.release();

        testChannel.close();
    }
}
