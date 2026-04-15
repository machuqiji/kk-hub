package com.kk.mila.websocket.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

public class SaTokenStompInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SaTokenStompInterceptor.class);

    private static final String HEADER_TOKEN = "token";
    private static final String SESSION_USER = "user";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader(HEADER_TOKEN);

            if (StrUtil.isBlank(token)) {
                log.warn("[STOMP Auth] No token provided in STOMP CONNECT headers");
                throw new IllegalArgumentException("No token provided");
            }

            try {
                StpUtil.checkLogin();
                Object loginId = StpUtil.getLoginId();
                accessor.getSessionAttributes().put(SESSION_USER, loginId);
                log.info("[STOMP Auth] User {} authenticated successfully", loginId);
            } catch (Exception e) {
                log.error("[STOMP Auth] Token validation failed: {}", e.getMessage());
                throw new IllegalArgumentException("Token validation failed: " + e.getMessage());
            }
        }

        return message;
    }
}
