package com.agw.auth.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class AppWsPushService {

    private final AppWsSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public AppWsPushService(AppWsSessionRegistry sessionRegistry, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    public void broadcast(String type, Map<String, Object> payload) {
        String body = toJson(type, payload);
        TextMessage message = new TextMessage(body);
        for (AppWsSessionRegistry.SessionBinding binding : sessionRegistry.allSessions()) {
            WebSocketSession session = binding.session();
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException ignored) {
                // ignore one dead session and continue fan-out
            }
        }
    }

    private String toJson(String type, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("timestamp", Instant.now().toEpochMilli());
        envelope.put("payload", payload == null ? Map.of() : payload);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            return "{\"type\":\"system.error\",\"payload\":{\"error\":\"serialization_failed\"}}";
        }
    }
}

