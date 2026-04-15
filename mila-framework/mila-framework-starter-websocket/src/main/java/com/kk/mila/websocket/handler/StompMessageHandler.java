package com.kk.mila.websocket.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class StompMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(StompMessageHandler.class);

    @MessageMapping("/chat/send")
    @SendTo("/topic/public")
    public String sendMessage(String message, SimpMessageHeaderAccessor headerAccessor) {
        String user = headerAccessor.getSessionAttributes().get("user") != null
                ? headerAccessor.getSessionAttributes().get("user").toString()
                : "anonymous";
        log.info("[STOMP] User {} sent message: {}", user, message);
        return message;
    }

    @MessageMapping("/chat/private")
    public void sendPrivateMessage(String message, SimpMessageHeaderAccessor headerAccessor) {
        log.info("[STOMP] Private message: {}", message);
    }
}
