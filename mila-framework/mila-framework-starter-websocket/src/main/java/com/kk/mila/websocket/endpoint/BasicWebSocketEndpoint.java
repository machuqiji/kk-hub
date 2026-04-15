package com.kk.mila.websocket.endpoint;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint(value = "/ws/basic")
public class BasicWebSocketEndpoint {

    private static final Logger log = LoggerFactory.getLogger(BasicWebSocketEndpoint.class);

    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();
    private static final ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        sessionMap.put(session.getId(), session);
        log.info("[BasicWS] Session opened: {}, total: {}", session.getId(), sessions.size());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("[BasicWS] Received from {}: {}", session.getId(), message);
        broadcast(message);
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        sessionMap.remove(session.getId());
        log.info("[BasicWS] Session closed: {}, total: {}", session.getId(), sessions.size());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("[BasicWS] Session error: {}", session.getId(), error);
    }

    public static void broadcast(String message) {
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("[BasicWS] Broadcast error to {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public static void sendToSession(String sessionId, String message) {
        Session session = sessionMap.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("[BasicWS] Send error to {}: {}", sessionId, e.getMessage());
            }
        }
    }

    public static int getActiveSessionCount() {
        return sessions.size();
    }
}
