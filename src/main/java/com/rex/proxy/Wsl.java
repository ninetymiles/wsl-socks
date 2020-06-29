package com.rex.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.net.URI;
import java.util.Properties;

/**
 * WebSocket proxy server
 */
public class Wsl {

    private static final Logger sLogger = LoggerFactory.getLogger(Wsl.class);

    static final String ENV_CONFIG = "WSL_CONF";

    public static class Configuration {
        public String bindAddress;
        public Integer bindPort;
        public Boolean ssl;
        public String sslCert;
        public String sslKey; // In PKCS8 format
        public String sslKeyPassword; // Leave it null if key not encrypted
        public String proxyUid; // Leave it null if do not need auth
        public String proxyPath; // Leave it null if accept all http path upgrading
        public Configuration() {
        }
        public Configuration(int port) {
            bindPort = port;
        }
        public Configuration(String addr, int port) {
            this(port);
            bindAddress = addr;
        }
        public Configuration(String addr, int port, String cert, String key) {
            this(addr, port);
            ssl = true;
            sslCert = cert;
            sslKey = key;
        }
        public Configuration(String addr, int port, String cert, String key, String keyPassword) {
            this(addr, port, cert, key);
            sslKeyPassword = keyPassword;
        }
        @Override
        public String toString() {
            StringBuffer buffer = new StringBuffer();
            buffer.append("<@");
            buffer.append(Integer.toHexString(hashCode()));
            buffer.append(" bindAddress:" + bindAddress);
            buffer.append(" bindPort:" + bindPort);
            buffer.append(" ssl:" + ssl);
            buffer.append(" sslCert:" + sslCert);
            buffer.append(" sslKey:" + sslKey);
            buffer.append(" sslKeyPassword:" + sslKeyPassword);
            buffer.append(" proxyUid:" + proxyUid);
            buffer.append(" proxyPath:" + proxyPath);
            buffer.append(">");
            return buffer.toString();
        }
    }
    private Configuration mConfig = new Configuration("0.0.0.0", 9777);

    /**
     * Construct the server
     */
    public Wsl() {
        sLogger.trace("<init>");
    }

    synchronized public Wsl config(Configuration conf) {
        if (conf.bindAddress != null) mConfig.bindAddress = conf.bindAddress;
        if (conf.bindPort != null) mConfig.bindPort = conf.bindPort;
        if (conf.ssl != null) mConfig.ssl = conf.ssl;
        if (conf.sslCert != null) mConfig.sslCert = conf.sslCert;
        if (conf.sslKey != null) mConfig.sslKey = conf.sslKey;
        if (conf.sslKeyPassword != null) mConfig.sslKeyPassword = conf.sslKeyPassword;
        if (conf.proxyUid != null) mConfig.proxyUid = conf.proxyUid;
        if (conf.proxyPath != null) mConfig.proxyPath = conf.proxyPath;
        return this;
    }

    // Start proxy server
    public WslServer server(Properties config) {
        WslServer server = new WslServer();
        WslServer.Configuration serverConf = new WslServer.Configuration();
        for (String name : config.stringPropertyNames()) {
            switch (name) {
            case "bindAddress":
                serverConf.bindAddress = config.getProperty(name);
                break;
            case "bindPort":
                serverConf.bindPort = Integer.parseInt(config.getProperty(name));
                break;
            case "ssl":
                serverConf.ssl = Boolean.parseBoolean(config.getProperty(name));
                break;
            case "sslCert":
                serverConf.sslCert = config.getProperty(name);
                break;
            case "sslKey":
                serverConf.sslKey = config.getProperty(name);
                break;
            case "sslKeyPassword":
                serverConf.sslKeyPassword = config.getProperty(name);
                break;
            case "proxyUid":
                serverConf.proxyUid = config.getProperty(name);
                break;
            case "proxyPath":
                serverConf.proxyPath = config.getProperty(name);
                break;
            }
        }
        try {
            server.config(serverConf);
            server.start();
            return server;
        } catch (Throwable tr) {
            sLogger.error("Failed to start server\n", tr);
        }
        return null;
    }

    // Start proxy local
    public WslLocal local(Properties config) {
        WslLocal local = new WslLocal();
        WslLocal.Configuration localConf = new WslLocal.Configuration();
        for (String name : config.stringPropertyNames()) {
            switch (name) {
            case "bindAddress":
                localConf.bindAddress = config.getProperty(name);
                break;
            case "bindPort":
                localConf.bindPort = Integer.parseInt(config.getProperty(name));
                break;
            case "authUser":
                localConf.authUser = config.getProperty(name);
                break;
            case "authPassword":
                localConf.authPassword = config.getProperty(name);
                break;
            case "proxyUri":
                localConf.proxyUri = URI.create(config.getProperty(name));
                break;
            case "proxyUid":
                localConf.proxyUid = config.getProperty(name);
                break;
            case "proxyCertVerify":
                localConf.proxyCertVerify = Boolean.parseBoolean(config.getProperty(name));
                break;
            }
        }
        try {
            local.config(localConf);
            local.start();
            return local;
        } catch (Throwable tr) {
            sLogger.error("Failed to start local\n", tr);
        }
        return null;
    }

    public static void main(String[] args) {
        Wsl wsl = new Wsl();
        String configFile = System.getProperty(ENV_CONFIG);
        if (System.getenv().containsKey(ENV_CONFIG)) {
            configFile = System.getenv(ENV_CONFIG);
        }

        int idx = 0;
        while (idx < args.length) {
            String key = args[idx++];
            if ("-c".equals(key) || "--config".equals(key)) {
                configFile = args[idx++];
            }
            if ("-h".equals(key) || "--help".equals(key)) {
                printHelp();
                return;
            }
        }

        try {
            Properties config = new Properties();
            config.load(new FileInputStream(configFile));
            String mode = config.getProperty("mode");
            if ("server".equalsIgnoreCase(mode)) {
                wsl.server(config);
            } else if ("local".equalsIgnoreCase(mode)) {
                wsl.local(config);
            } else {
                sLogger.warn("Not specify mode");
            }
        } catch (Exception ex) {
            sLogger.warn("Failed to load config file " + configFile + "\n", ex);
            printHelp();
        }
    }

    private static void printHelp() {
        System.out.println("Usage: WslSocks [options]");
        System.out.println("    -c | --config   Configuration file");
        System.out.println("    -h | --help     Help page");
    }
}
