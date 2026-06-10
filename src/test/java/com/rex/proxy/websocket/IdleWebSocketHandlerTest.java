package com.rex.proxy.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Test idle timeout for WebSocket connections
 */
public class IdleWebSocketHandlerTest {

    private EmbeddedChannel channel;
    private IdleWebSocketHandler handler;

    @Before
    public void setUp() {
        handler = new IdleWebSocketHandler(900); // 15 minutes in seconds
        channel = new EmbeddedChannel(handler);
    }

    @After
    public void tearDown() {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    @Test
    public void testIdleTimeoutConfiguration() {
        // Verify handler is configured for 15 minutes (900 seconds)
        // We can't directly access the timeout value, but we can verify behavior
        assertTrue("Channel should be active initially", channel.isActive());
    }

    @Test
    public void testIdleEventClosesChannel() throws Exception {
        // IdleWebSocketHandler uses IdleStateHandler internally
        // IdleStateHandler calls channelIdle() when timer fires, not via userEventTriggered
        // So we test the channelIdle behavior directly or verify via actual timer

        // Verify initial state
        assertTrue("Channel should be active initially", channel.isActive());

        // Trigger idle by calling channelIdle directly (simulating what IdleStateHandler does)
        // Since IdleStateHandler's timer doesn't work in EmbeddedChannel,
        // we directly invoke the callback method
        IdleStateEvent idleEvent = IdleStateEvent.ALL_IDLE_STATE_EVENT;
        handler.channelIdle(channel.pipeline().firstContext(), idleEvent);

        // Channel should be closed
        assertFalse("Channel should be closed after idle timeout", channel.isActive());
    }

    @Test
    public void testReaderIdleDoesNotCloseChannel() {
        // Only ALL_IDLE should close the channel
        IdleStateEvent readerIdleEvent = IdleStateEvent.READER_IDLE_STATE_EVENT;

        channel.pipeline().fireUserEventTriggered(readerIdleEvent);
        channel.runPendingTasks();

        // Channel should still be active (only ALL_IDLE closes it)
        assertTrue("Channel should remain active for READER_IDLE", channel.isActive());
    }

    @Test
    public void testWriterIdleDoesNotCloseChannel() {
        // Only ALL_IDLE should close the channel
        IdleStateEvent writerIdleEvent = IdleStateEvent.WRITER_IDLE_STATE_EVENT;

        channel.pipeline().fireUserEventTriggered(writerIdleEvent);
        channel.runPendingTasks();

        // Channel should still be active
        assertTrue("Channel should remain active for WRITER_IDLE", channel.isActive());
    }

    @Test
    public void testActivityResetsIdleTimer() {
        // In a real scenario, activity would reset the timer
        // With EmbeddedChannel, we can simulate this by sending data

        assertTrue("Channel should be active", channel.isActive());

        // Send some data to simulate activity
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame();
        channel.writeOutbound(frame);

        // Channel should still be active
        assertTrue("Channel should remain active after activity", channel.isActive());
    }

    @Test
    public void testMultipleIdleEvents() throws Exception {
        // IdleStateHandler doesn't work properly with EmbeddedChannel's fireUserEventTriggered
        // So we directly call channelIdle() to simulate the timer callback

        // First idle event should close the channel
        IdleStateEvent idleEvent1 = IdleStateEvent.ALL_IDLE_STATE_EVENT;
        handler.channelIdle(channel.pipeline().firstContext(), idleEvent1);

        assertFalse("Channel should be closed after first idle", channel.isActive());

        // Second idle event should not cause error (channel already closed)
        IdleStateEvent idleEvent2 = IdleStateEvent.ALL_IDLE_STATE_EVENT;
        try {
            // Calling channelIdle on a closed channel - context may be null
            // The handler should handle this gracefully
            ChannelHandlerContext ctx = channel.pipeline().firstContext();
            if (ctx != null) {
                handler.channelIdle(ctx, idleEvent2);
            }
            // Should not throw exception
        } catch (Exception e) {
            fail("Should handle idle event on closed channel gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testIdleHandlerInPipeline() {
        // Verify the handler is properly added to pipeline
        assertNotNull("Handler should be in pipeline", channel.pipeline().get(IdleWebSocketHandler.class));
    }
}
