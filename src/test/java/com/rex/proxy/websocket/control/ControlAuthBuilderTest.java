package com.rex.proxy.websocket.control;

import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.assertEquals;

public class ControlAuthBuilderTest {

    private final static byte[] NONCE = new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06 };
    private final static String SECRET = "SECRET";

    @Test
    public void testByteArrayNonce() {
        String credential = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .setAddress("www.amazon.com")
                .setPort(443)
                .build();
        assertEquals("7BOmk0M59hQn210grnwI2ovh83p3NlhTq77pbvKarcg=", credential);
    }

    @Test
    public void testBase64Nonce() {
        String credential = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(Base64.getEncoder().encodeToString(NONCE))
                .setAddress("www.amazon.com")
                .setPort(443)
                .build();
        assertEquals("7BOmk0M59hQn210grnwI2ovh83p3NlhTq77pbvKarcg=", credential);
    }

    @Test
    public void testUUIDSecret() {
        String credential = new ControlAuthBuilder()
                .setSecret("c5c7c9d7-051c-4356-b133-0d04ba6a0a41")
                .setNonce("/lei32T2SfQI5PXh4ZQpkdXka14T4J5m5QNZWivIgi8=")
                .setAddress("www.amazon.com")
                .setPort(443)
                .build();
        assertEquals("GAnwgrevgMdIAmWgzOX+nwWFc4WCJZJQIuhjyQXaed0=", credential);
    }
}
