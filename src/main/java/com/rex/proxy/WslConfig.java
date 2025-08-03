package com.rex.proxy;

import java.net.URI;
import java.util.Properties;

/**
 * WebSocket proxy server
 */
public class WslConfig {

    public String mode;
    public String bindAddress;
    public Integer bindPort;
    public String bindProtocol;
    public Boolean ssl;
    public String sslCert;
    public String sslKey; // In PKCS8 format
    public String sslKeyPassword; // Leave it null if key not encrypted
    public String authUser;
    public String authPassword;
    public URI proxyUri;
    public String proxyUid; // Leave it null if you do not need auth
    public String proxyPath; // Leave it null if accept all http path upgrading

    public WslConfig() {
    }

    public WslConfig(int port) {
        bindPort = port;
    }

    public WslConfig(String addr, int port) {
        this(port);
        bindAddress = addr;
    }

    public WslConfig(String addr, int port, String cert, String key) {
        this(addr, port);
        ssl = true;
        sslCert = cert;
        sslKey = key;
    }

    public WslConfig(String addr, int port, String cert, String key, String keyPassword) {
        this(addr, port, cert, key);
        sslKeyPassword = keyPassword;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<@");
        builder.append(Integer.toHexString(hashCode()));
        builder.append(" mode:").append(mode);
        builder.append(" bindAddress:").append(bindAddress);
        builder.append(" bindPort:").append(bindPort);
        builder.append(" bindProtocol:").append(bindProtocol);
        builder.append(" ssl:").append(ssl);
        builder.append(" sslCert:").append(sslCert);
        builder.append(" sslKey:").append(sslKey);
        builder.append(" sslKeyPassword:").append(sslKeyPassword);
        builder.append(" authUser:").append(authUser);
        builder.append(" authPassword:").append(authPassword);
        builder.append(" proxyUri:").append(proxyUri);
        builder.append(" proxyUid:").append(proxyUid);
        builder.append(" proxyPath:").append(proxyPath);
        builder.append(">");
        return builder.toString();
    }

    public static class Builder {

        private WslConfig mConfig = new WslConfig();

        public Builder() {
        }

        public Builder(WslConfig conf) {
            if (conf.mode != null) mConfig.mode = conf.mode;
            if (conf.bindAddress != null) mConfig.bindAddress = conf.bindAddress;
            if (conf.bindPort != null) mConfig.bindPort = conf.bindPort;
            if (conf.bindProtocol != null) mConfig.bindProtocol = conf.bindProtocol;
            if (conf.ssl != null) mConfig.ssl = conf.ssl;
            if (conf.sslCert != null) mConfig.sslCert = conf.sslCert;
            if (conf.sslKey != null) mConfig.sslKey = conf.sslKey;
            if (conf.sslKeyPassword != null) mConfig.sslKeyPassword = conf.sslKeyPassword;
            if (conf.authUser != null) mConfig.authUser = conf.authUser;
            if (conf.authPassword != null) mConfig.authPassword = conf.authPassword;
            if (conf.proxyUri != null) mConfig.proxyUri = conf.proxyUri;
            if (conf.proxyUid != null) mConfig.proxyUid = conf.proxyUid;
            if (conf.proxyPath != null) mConfig.proxyPath = conf.proxyPath;
        }

        public Builder(Properties properties) {
            for (String name : properties.stringPropertyNames()) {
                switch (name) {
                case "mode":
                    mConfig.mode = properties.getProperty("mode");
                    break;
                case "bindAddress":
                    mConfig.bindAddress = properties.getProperty(name);
                    break;
                case "bindPort":
                    mConfig.bindPort = Integer.parseInt(properties.getProperty(name));
                    break;
                case "ssl":
                    mConfig.ssl = Boolean.parseBoolean(properties.getProperty(name));
                    break;
                case "sslCert":
                    mConfig.sslCert = properties.getProperty(name);
                    break;
                case "sslKey":
                    mConfig.sslKey = properties.getProperty(name);
                    break;
                case "sslKeyPassword":
                    mConfig.sslKeyPassword = properties.getProperty(name);
                    break;
                case "authUser":
                    mConfig.authUser = properties.getProperty(name);
                    break;
                case "authPassword":
                    mConfig.authPassword = properties.getProperty(name);
                    break;
                case "proxyUri":
                    mConfig.proxyUri = URI.create(properties.getProperty(name));
                    break;
                case "proxyUid":
                    mConfig.proxyUid = properties.getProperty(name);
                    break;
                case "proxyPath":
                    mConfig.proxyPath = properties.getProperty(name);
                    break;
                }
            }
        }

        public Builder setBindAddress(String value) {
            mConfig.bindAddress = value;
            return this;
        }

        public Builder setBindPort(int value) {
            mConfig.bindPort = value;
            return this;
        }

        public Builder setBindProtocol(String value) {
            mConfig.bindProtocol = value;
            return this;
        }

        public Builder setSsl(boolean value) {
            mConfig.ssl = value;
            return this;
        }

        public Builder setSslCert(String value) {
            mConfig.sslCert = value;
            return this;
        }

        public Builder setSslKey(String value) {
            mConfig.sslKey = value;
            return this;
        }

        public Builder setSslKeyPassword(String value) {
            mConfig.sslKeyPassword = value;
            return this;
        }

        public Builder setProxyUid(String value) {
            mConfig.proxyUid = value;
            return this;
        }

        public Builder setProxyPath(String value) {
            mConfig.proxyPath = value;
            return this;
        }

        public WslConfig build() {
            return mConfig;
        }
    }
}
