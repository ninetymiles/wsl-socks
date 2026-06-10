package com.rex.proxy.websocket.control;

import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ControlAuthBuilderTest {

    private final static byte[] NONCE = new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06 };
    private final static String SECRET = "SECRET";

    // ========== Original tests for old protocol (per-request token with address+port) ==========

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

    // ========== New tests for new protocol (authorization token without address+port) ==========

    @Test
    public void testNewProtocolAuthorizationTokenWithoutAddressPort() {
        // New protocol: authorization uses only secret + nonce (no address/port)
        String token = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .build();  // No address/port

        // Verify it produces a valid token (different from old protocol)
        assertEquals("8QlB5Q0XD1/ky/xtFMHusv6X9tokLyFI1iaJRowI55w=", token);
    }

    @Test
    public void testNewProtocolVsOldProtocolTokenDifference() {
        // New protocol: only secret + nonce
        String newToken = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .build();

        // Old protocol: secret + nonce + address + port
        String oldToken = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .setAddress("www.amazon.com")
                .setPort(443)
                .build();

        // Tokens should be different
        assertEquals("8QlB5Q0XD1/ky/xtFMHusv6X9tokLyFI1iaJRowI55w=", newToken);
        assertEquals("7BOmk0M59hQn210grnwI2ovh83p3NlhTq77pbvKarcg=", oldToken);
        assertNotEquals("New and old protocol tokens should differ", newToken, oldToken);
    }

    @Test
    public void testSameInputsProduceSameAuthorizationToken() {
        String token1 = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .build();

        String token2 = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .build();

        assertEquals("Same inputs should produce same token", token1, token2);
    }

    @Test
    public void testDifferentNoncesProduceDifferentTokens() {
        byte[] nonce2 = new byte[] { (byte) 0xFF, (byte) 0xFE, (byte) 0xFD };

        String token1 = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .build();

        String token2 = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(nonce2)
                .build();

        // Different nonces produce different tokens
        assertEquals("8QlB5Q0XD1/ky/xtFMHusv6X9tokLyFI1iaJRowI55w=", token1);
        assertEquals("QVGge5mA1n+KkEi/2qi2jzys30NQi0R8RdiNZJkwUs8=", token2);
        assertNotEquals("Different nonces should produce different tokens", token1, token2);
    }

    @Test
    public void testAddressAndPortOnlyAffectOldProtocol() {
        // Without address/port (new protocol authorization)
        String authToken = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .build();

        // With address/port (old protocol per-request)
        String requestToken1 = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .setAddress("host1.example.com")
                .setPort(443)
                .build();

        String requestToken2 = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .setAddress("host2.example.com")
                .setPort(443)
                .build();

        // Authorization token should be consistent (no address/port)
        assertEquals("8QlB5Q0XD1/ky/xtFMHusv6X9tokLyFI1iaJRowI55w=", authToken);

        // Request tokens should differ (different addresses)
        assertNotEquals("Different addresses should produce different request tokens",
                requestToken1, requestToken2);

        // Both request tokens should differ from authorization token
        assertNotEquals("Request token should differ from authorization token",
                authToken, requestToken1);
        assertNotEquals("Request token should differ from authorization token",
                authToken, requestToken2);
    }

    @Test
    public void testDifferentPortsProduceDifferentOldProtocolTokens() {
        String token1 = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .setAddress("www.example.com")
                .setPort(80)
                .build();

        String token2 = new ControlAuthBuilder()
                .setSecret(SECRET)
                .setNonce(NONCE)
                .setAddress("www.example.com")
                .setPort(443)
                .build();

        assertNotEquals("Different ports should produce different tokens in old protocol",
                token1, token2);
    }
}
