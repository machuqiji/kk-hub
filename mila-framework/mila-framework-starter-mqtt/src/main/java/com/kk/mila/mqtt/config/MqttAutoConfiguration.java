package com.kk.mila.mqtt.config;

import com.kk.mila.mqtt.handler.MqttMessageHandler;
import com.kk.mila.mqtt.service.IotDataCollector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultMqttPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttPahoMessageConverter;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;

import java.util.UUID;

@Configuration
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnClass(MqttPahoClientFactory.class)
@ConditionalOnProperty(prefix = "mila.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttAutoConfiguration {

    @Bean
    public MqttPahoClientFactory mqttPahoClientFactory(MqttProperties properties) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setServerURIs(properties.getBrokerUrl());

        if (properties.getUsername() != null && !properties.getUsername().isEmpty()) {
            factory.setUserName(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
            factory.setPassword(properties.getPassword());
        }

        factory.setAutomaticReconnect(properties.isAutomaticReconnect());
        factory.setConnectionTimeout(properties.getConnectionTimeout());

        String clientId = properties.getClientId();
        if (clientId == null || clientId.isEmpty()) {
            clientId = "mila-" + UUID.randomUUID().toString();
        }
        factory.setClientId(clientId);

        return factory;
    }

    @Bean
    public MqttPahoMessageConverter mqttPahoMessageConverter(MqttProperties properties) {
        DefaultMqttPahoMessageConverter converter = new DefaultMqttPahoMessageConverter();
        converter.setDefaultQos(properties.getDefaultQos());
        return converter;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundChannelAdapter(
            MqttProperties properties,
            MqttPahoClientFactory clientFactory,
            MqttPahoMessageConverter messageConverter,
            MessageChannel mqttInputChannel) {

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter("mila-mqtt-adapter", clientFactory, properties.getSubscribedTopics());
        adapter.setConverter(messageConverter);
        adapter.setOutputChannel(mqttInputChannel);
        adapter.setQos(properties.getDefaultQos());
        return adapter;
    }

    @Bean
    public IotDataCollector iotDataCollector(MqttProperties properties) {
        return new IotDataCollector(properties);
    }

    @Bean
    public MqttMessageHandler mqttMessageHandler(
            MqttProperties properties,
            IotDataCollector iotDataCollector) {
        return new MqttMessageHandler(properties, iotDataCollector);
    }
}
