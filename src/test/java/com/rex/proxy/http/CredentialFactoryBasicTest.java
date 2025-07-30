package com.rex.proxy.http;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class CredentialFactoryBasicTest {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Test");

    @Test
    public void testBasicAuth() throws Exception {
        CredentialFactoryBasic factory = new CredentialFactoryBasic("account", "password");
        String credential = factory.create();
        sLogger.trace("credential=<{}>", credential);
        assertEquals("Basic YWNjb3VudDpwYXNzd29yZA==", credential);
    }
}