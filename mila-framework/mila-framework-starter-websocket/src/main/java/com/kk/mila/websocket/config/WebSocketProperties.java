package com.kk.mila.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mila.websocket")
public class WebSocketProperties {

    private boolean enabled = true;
    private boolean basicEnabled = true;
    private String basicPath = "/ws";
    private String[] basicAllowedOrigins = {"*"};
    private boolean stompEnabled = true;
    private String stompEndpoint = "/ws-stomp";
    private String[] stompAllowedOrigins = {"*"};
    private boolean sockJsEnabled = true;
    private Broker broker = new Broker();

    public static class Broker {
        private String type = "rabbitmq";
        private boolean relayEnabled = false;
        private String relayHost = "localhost";
        private int relayPort = 61613;
        private String relayUser = "guest";
        private String relayPassword = "guest";
        private String virtualHost = "/";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isRelayEnabled() {
            return relayEnabled;
        }

        public void setRelayEnabled(boolean relayEnabled) {
            this.relayEnabled = relayEnabled;
        }

        public String getRelayHost() {
            return relayHost;
        }

        public void setRelayHost(String relayHost) {
            this.relayHost = relayHost;
        }

        public int getRelayPort() {
            return relayPort;
        }

        public void setRelayPort(int relayPort) {
            this.relayPort = relayPort;
        }

        public String getRelayUser() {
            return relayUser;
        }

        public void setRelayUser(String relayUser) {
            this.relayUser = relayUser;
        }

        public String getRelayPassword() {
            return relayPassword;
        }

        public void setRelayPassword(String relayPassword) {
            this.relayPassword = relayPassword;
        }

        public String getVirtualHost() {
            return virtualHost;
        }

        public void setVirtualHost(String virtualHost) {
            this.virtualHost = virtualHost;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBasicEnabled() {
        return basicEnabled;
    }

    public void setBasicEnabled(boolean basicEnabled) {
        this.basicEnabled = basicEnabled;
    }

    public String getBasicPath() {
        return basicPath;
    }

    public void setBasicPath(String basicPath) {
        this.basicPath = basicPath;
    }

    public String[] getBasicAllowedOrigins() {
        return basicAllowedOrigins;
    }

    public void setBasicAllowedOrigins(String[] basicAllowedOrigins) {
        this.basicAllowedOrigins = basicAllowedOrigins;
    }

    public boolean isStompEnabled() {
        return stompEnabled;
    }

    public void setStompEnabled(boolean stompEnabled) {
        this.stompEnabled = stompEnabled;
    }

    public String getStompEndpoint() {
        return stompEndpoint;
    }

    public void setStompEndpoint(String stompEndpoint) {
        this.stompEndpoint = stompEndpoint;
    }

    public String[] getStompAllowedOrigins() {
        return stompAllowedOrigins;
    }

    public void setStompAllowedOrigins(String[] stompAllowedOrigins) {
        this.stompAllowedOrigins = stompAllowedOrigins;
    }

    public boolean isSockJsEnabled() {
        return sockJsEnabled;
    }

    public void setSockJsEnabled(boolean sockJsEnabled) {
        this.sockJsEnabled = sockJsEnabled;
    }

    public Broker getBroker() {
        return broker;
    }

    public void setBroker(Broker broker) {
        this.broker = broker;
    }
}
