package com.kk.mila.mqtt.handler;

import com.kk.mila.mqtt.config.MqttProperties;
import com.kk.mila.mqtt.service.IotDataCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MqttMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final MqttProperties properties;
    private final IotDataCollector iotDataCollector;
    private final Pattern deviceIdPattern;
    private final Map<String, DeviceMessageType> topicTypeCache = new ConcurrentHashMap<>();

    public enum DeviceMessageType {
        TELEMETRY,
        STATUS,
        COMMAND,
        UNKNOWN
    }

    public MqttMessageHandler(MqttProperties properties, IotDataCollector iotDataCollector) {
        this.properties = properties;
        this.iotDataCollector = iotDataCollector;
        this.deviceIdPattern = Pattern.compile(properties.getDeviceIdPattern());
    }

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        String topic = (String) message.getHeaders().get("mqtt_topic");
        Object payload = message.getPayload();

        if (topic == null || payload == null) {
            log.warn("Received MQTT message with null topic or payload");
            return;
        }

        log.debug("Received MQTT message on topic: {}, payload: {}", topic, payload);

        try {
            DeviceMessageInfo info = parseTopic(topic);
            if (info == null) {
                log.warn("Could not parse device ID from topic: {}", topic);
                return;
            }

            String deviceId = info.deviceId;
            DeviceMessageType messageType = info.messageType;

            switch (messageType) {
                case TELEMETRY:
                    iotDataCollector.collectTelemetry(deviceId, topic, payload);
                    break;
                case STATUS:
                    iotDataCollector.collectStatus(deviceId, topic, payload);
                    break;
                case COMMAND:
                    iotDataCollector.processCommand(deviceId, topic, payload);
                    break;
                default:
                    iotDataCollector.collectRawData(deviceId, topic, payload);
            }
        } catch (Exception e) {
            log.error("Error processing MQTT message from topic {}: {}", topic, e.getMessage(), e);
        }
    }

    private DeviceMessageInfo parseTopic(String topic) {
        Matcher matcher = deviceIdPattern.matcher(topic);
        if (!matcher.find()) {
            return null;
        }

        String deviceId = matcher.group(1);
        DeviceMessageType messageType = topicTypeCache.computeIfAbsent(topic, this::determineMessageType);

        return new DeviceMessageInfo(deviceId, messageType);
    }

    private DeviceMessageType determineMessageType(String topic) {
        if (topic.endsWith("/telemetry")) {
            return DeviceMessageType.TELEMETRY;
        } else if (topic.endsWith("/status")) {
            return DeviceMessageType.STATUS;
        } else if (topic.endsWith("/command") || topic.contains("/commands/")) {
            return DeviceMessageType.COMMAND;
        }
        return DeviceMessageType.UNKNOWN;
    }

    private static class DeviceMessageInfo {
        final String deviceId;
        final DeviceMessageType messageType;

        DeviceMessageInfo(String deviceId, DeviceMessageType messageType) {
            this.deviceId = deviceId;
            this.messageType = messageType;
        }
    }
}
