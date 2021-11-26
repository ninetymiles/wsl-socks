package com.rex.proxy.websocket.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * WebSocket control auth credential builder
 *
 * CREDENTIAL = HMAC.init(SECRET).update(NONCE).update(address).update(port)
 *
 */
public class ControlAuthBuilder {

    private static final Logger sLogger = LoggerFactory.getLogger(ControlAuthBuilder.class);

    public String mAlgorithm = "HmacSHA256";
    public String mSecret;
    public byte[] mNonce;

    public String mAddress;
    public Integer mPort;

    public ControlAuthBuilder setAlgorithm(String algorithm) {
        mAlgorithm = algorithm;
        return this;
    }

    public ControlAuthBuilder setSecret(String secret) {
        mSecret = secret;
        return this;
    }

    public ControlAuthBuilder setNonce(String nonce64) {
        mNonce = Base64.getDecoder().decode(nonce64);
        return this;
    }

    public ControlAuthBuilder setNonce(byte[] nonce) {
        mNonce = nonce;
        return this;
    }

    public ControlAuthBuilder setAddress(String address) {
        mAddress = address;
        return this;
    }

    public ControlAuthBuilder setPort(int port) {
        mPort = port;
        return this;
    }

    public String build() {
        String credential = null;
        try {
            Mac hmac = Mac.getInstance(mAlgorithm);
            hmac.init(new SecretKeySpec(mSecret.getBytes(), mAlgorithm));
            hmac.update(mNonce);
            hmac.update(mAddress.getBytes());
            hmac.update(ByteBuffer.allocate(Integer.BYTES).putInt(mPort).array());
            credential = Base64.getEncoder().encodeToString(hmac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            sLogger.warn("Failed to build auth credential - {}", ex.getMessage());
        }
        return credential;
    }
}
