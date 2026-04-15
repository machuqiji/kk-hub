package com.kk.mila.websocket.config;

import com.kk.mila.websocket.interceptor.SaTokenStompInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelInterceptorRegistry;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
@ConditionalOnClass(WebSocketMessageBrokerConfigurer.class)
@ConditionalOnProperty(prefix = "mila.websocket", name = "stomp-enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketStompConfig.class);

    private final WebSocketProperties properties;

    public WebSocketStompConfig(WebSocketProperties properties) {
        this.properties = properties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        WebSocketProperties.Broker broker = properties.getBroker();

        if (broker.isRelayEnabled()) {
            if ("kafka".equalsIgnoreCase(broker.getType())) {
                log.warn("[STOMP] Kafka STOMP relay not fully implemented, falling back to simple broker");
                registry.enableSimpleBroker("/topic", "/queue");
            } else {
                registry.enableStompBrokerRelay("/topic", "/queue", "/exchange")
                        .setRelayHost(broker.getRelayHost())
                        .setRelayPort(broker.getRelayPort())
                        .setClientLogin(broker.getRelayUser())
                        .setClientPasscode(broker.getRelayPassword())
                        .setSystemLogin(broker.getRelayUser())
                        .setSystemPasscode(broker.getRelayPassword())
                        .setVirtualHost(broker.getVirtualHost());
            }
            log.info("[STOMP] Using {} broker relay at {}:{}", broker.getType(), broker.getRelayHost(), broker.getRelayPort());
        } else {
            registry.enableSimpleBroker("/topic", "/queue");
            log.info("[STOMP] Using in-memory simple broker");
        }

        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var registration = registry.addEndpoint(properties.getStompEndpoint())
                .setAllowedOriginPatterns(properties.getStompAllowedOrigins());
        if (properties.isSockJsEnabled()) {
            registration.withSockJS();
        }
        log.info("[STOMP] Registered endpoint: {}", properties.getStompEndpoint());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(8192 * 1024)
                .setSendBufferSizeLimit(8192 * 1024)
                .setSendTimeLimit(20000);
    }

    @Override
    public void configureClientInboundChannel(ChannelInterceptorRegistry registry) {
        registry.addInterceptor(saTokenStompInterceptor());
    }

    @Bean
    public SaTokenStompInterceptor saTokenStompInterceptor() {
        return new SaTokenStompInterceptor();
    }
}
