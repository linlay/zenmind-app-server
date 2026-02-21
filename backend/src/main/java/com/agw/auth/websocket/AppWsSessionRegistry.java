package com.agw.auth.websocket;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.agw.auth.security.AppPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class AppWsSessionRegistry {

    private final Map<String, SessionBinding> sessionById = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, AppPrincipal principal) {
        if (session == null || principal == null) {
            return;
        }
        sessionById.put(session.getId(), new SessionBinding(session, principal));
    }

    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }
        sessionById.remove(session.getId());
    }

    public Collection<SessionBinding> allSessions() {
        return sessionById.values();
    }

    public record SessionBinding(WebSocketSession session, AppPrincipal principal) {
        public UUID deviceId() {
            return principal.deviceId();
        }
    }
}

