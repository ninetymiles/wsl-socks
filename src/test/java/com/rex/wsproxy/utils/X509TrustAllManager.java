package com.rex.wsproxy.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Customize the TrustManager to trust all certificates by not throwing any exception in callbacks
 */
public class X509TrustAllManager implements X509TrustManager {

    private final Logger mLogger = LoggerFactory.getLogger(X509TrustAllManager.class.getSimpleName());

    private final boolean DEBUG;

    public X509TrustAllManager() {
        this(false);
    }

    public X509TrustAllManager(boolean debug) {
        DEBUG = debug;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        if (DEBUG) {
            mLogger.debug("authType:{}", authType);
            for (X509Certificate cert : chain) {
                mLogger.debug("client s:{}", cert.getSubjectX500Principal().getName());
                mLogger.debug("       i:{}", cert.getIssuerX500Principal().getName());
            }
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        if (DEBUG) {
            mLogger.debug("authType:{}", authType);
            for (X509Certificate cert : chain) {
                mLogger.debug("server s:{}", cert.getSubjectX500Principal().getName());
                mLogger.debug("       i:{}", cert.getIssuerX500Principal().getName());
            }
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[] {}; // Must not null
    }
}
