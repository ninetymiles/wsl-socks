package com.rex.proxy.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;

import java.nio.charset.StandardCharsets;

public class CredentialFactoryBasic implements CredentialFactory {

    private String mUser;
    private String mPassword;

    public CredentialFactoryBasic(String user, String password) {
        mUser = user;
        mPassword = password;
    }

    @Override
    public String create() {
        String credential = mUser + ":" + mPassword;
        ByteBuf credential64 = Base64.encode(Unpooled.wrappedBuffer(credential.getBytes(StandardCharsets.UTF_8)), false);
        return "Basic " + credential64.toString(StandardCharsets.UTF_8);
    }
}
