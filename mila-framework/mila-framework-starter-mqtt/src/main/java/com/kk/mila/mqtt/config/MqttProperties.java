package com.kk.mila.mqtt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mila.mqtt")
public class MqttProperties {

    private boolean enabled = true;
    private String brokerUrl = "tcp://localhost:1883";
    private String clientId;
    private String username;
    private String password;
    private int defaultQos = 1;
    private boolean automaticReconnect = true;
    private int connectionTimeout = 30;
    private boolean sslEnabled = false;
    private String sslCaFile;
    private String sslClientCertFile;
    private String sslClientKeyFile;
    private String[] subscribedTopics = {"devices/+/telemetry", "devices/+/status"};
    private int[] subscribedQos;
    private String deviceIdPattern = "devices/([^/]+)/";
    private boolean storeDeviceData = false;
    private long deviceDataTtlSeconds = 86400;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDefaultQos() {
        return defaultQos;
    }

    public void setDefaultQos(int defaultQos) {
        this.defaultQos = defaultQos;
    }

    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }

    public void setAutomaticReconnect(boolean automaticReconnect) {
        this.automaticReconnect = automaticReconnect;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public String getSslCaFile() {
        return sslCaFile;
    }

    public void setSslCaFile(String sslCaFile) {
        this.sslCaFile = sslCaFile;
    }

    public String getSslClientCertFile() {
        return sslClientCertFile;
    }

    public void setSslClientCertFile(String sslClientCertFile) {
        this.sslClientCertFile = sslClientCertFile;
    }

    public String getSslClientKeyFile() {
        return sslClientKeyFile;
    }

    public void setSslClientKeyFile(String sslClientKeyFile) {
        this.sslClientKeyFile = sslClientKeyFile;
    }

    public String[] getSubscribedTopics() {
        return subscribedTopics;
    }

    public void setSubscribedTopics(String[] subscribedTopics) {
        this.subscribedTopics = subscribedTopics;
    }

    public int[] getSubscribedQos() {
        return subscribedQos;
    }

    public void setSubscribedQos(int[] subscribedQos) {
        this.subscribedQos = subscribedQos;
    }

    public String getDeviceIdPattern() {
        return deviceIdPattern;
    }

    public void setDeviceIdPattern(String deviceIdPattern) {
        this.deviceIdPattern = deviceIdPattern;
    }

    public boolean isStoreDeviceData() {
        return storeDeviceData;
    }

    public void setStoreDeviceData(boolean storeDeviceData) {
        this.storeDeviceData = storeDeviceData;
    }

    public long getDeviceDataTtlSeconds() {
        return deviceDataTtlSeconds;
    }

    public void setDeviceDataTtlSeconds(long deviceDataTtlSeconds) {
        this.deviceDataTtlSeconds = deviceDataTtlSeconds;
    }
}
