package com.kk.mila.mqtt.service;

import com.kk.mila.mqtt.config.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IotDataCollector {

    private static final Logger log = LoggerFactory.getLogger(IotDataCollector.class);

    private final MqttProperties properties;
    private final Map<String, DeviceData> deviceDataCache = new ConcurrentHashMap<>();

    public static class DeviceData {
        private String deviceId;
        private long lastTelemetryTime;
        private long lastStatusTime;
        private String lastStatus;
        private Map<String, Object> telemetryData;
        private Map<String, Object> metadata;

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public long getLastTelemetryTime() {
            return lastTelemetryTime;
        }

        public void setLastTelemetryTime(long lastTelemetryTime) {
            this.lastTelemetryTime = lastTelemetryTime;
        }

        public long getLastStatusTime() {
            return lastStatusTime;
        }

        public void setLastStatusTime(long lastStatusTime) {
            this.lastStatusTime = lastStatusTime;
        }

        public String getLastStatus() {
            return lastStatus;
        }

        public void setLastStatus(String lastStatus) {
            this.lastStatus = lastStatus;
        }

        public Map<String, Object> getTelemetryData() {
            return telemetryData;
        }

        public void setTelemetryData(Map<String, Object> telemetryData) {
            this.telemetryData = telemetryData;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    public IotDataCollector(MqttProperties properties) {
        this.properties = properties;
    }

    public void collectTelemetry(String deviceId, String topic, Object payload) {
        log.info("Collecting telemetry from device: {}, topic: {}", deviceId, topic);

        DeviceData data = deviceDataCache.computeIfAbsent(deviceId, id -> {
            DeviceData newData = new DeviceData();
            newData.setDeviceId(id);
            return newData;
        });

        data.setLastTelemetryTime(System.currentTimeMillis());

        if (payload instanceof Map) {
            data.setTelemetryData((Map<String, Object>) payload);
        }

        if (properties.isStoreDeviceData()) {
            storeDeviceData(deviceId, "telemetry", payload);
        }
    }

    public void collectStatus(String deviceId, String topic, Object payload) {
        log.info("Collecting status from device: {}, topic: {}, payload: {}", deviceId, topic, payload);

        DeviceData data = deviceDataCache.computeIfAbsent(deviceId, id -> {
            DeviceData newData = new DeviceData();
            newData.setDeviceId(id);
            return newData;
        });

        data.setLastStatusTime(System.currentTimeMillis());
        if (payload instanceof String) {
            data.setLastStatus((String) payload);
        }

        if (properties.isStoreDeviceData()) {
            storeDeviceData(deviceId, "status", payload);
        }
    }

    public void processCommand(String deviceId, String topic, Object payload) {
        log.info("Processing command for device: {}, topic: {}, payload: {}", deviceId, topic, payload);
    }

    public void collectRawData(String deviceId, String topic, Object payload) {
        log.debug("Collecting raw data from device: {}, topic: {}", deviceId, topic);
        if (properties.isStoreDeviceData()) {
            storeDeviceData(deviceId, "raw", payload);
        }
    }

    private void storeDeviceData(String deviceId, String dataType, Object payload) {
        log.debug("Storing {} data for device {}: {}", dataType, deviceId, payload);
    }

    public DeviceData getDeviceData(String deviceId) {
        return deviceDataCache.get(deviceId);
    }

    public Map<String, DeviceData> getAllDeviceData() {
        return new ConcurrentHashMap<>(deviceDataCache);
    }
}
