package com.agw.auth.websocket;

import java.time.Instant;
import java.util.Map;

import com.agw.auth.security.AppPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AppWsHandler extends TextWebSocketHandler {

    private final AppWsSessionRegistry sessionRegistry;
    private final AppWsPushService pushService;

    public AppWsHandler(AppWsSessionRegistry sessionRegistry, AppWsPushService pushService) {
        this.sessionRegistry = sessionRegistry;
        this.pushService = pushService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object principalObj = session.getAttributes().get(AppWsAuthHandshakeInterceptor.ATTR_APP_PRINCIPAL);
        if (!(principalObj instanceof AppPrincipal principal)) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing principal"));
            } catch (Exception ignored) {
            }
            return;
        }

        sessionRegistry.register(session, principal);
        pushService.broadcast("system.ping", Map.of("ts", Instant.now().toEpochMilli()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload == null ? "" : payload.trim())) {
            try {
                session.sendMessage(new TextMessage("{\"type\":\"system.ping\",\"payload\":{\"ts\":" + Instant.now().toEpochMilli() + "}}"));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessionRegistry.unregister(session);
        super.handleTransportError(session, exception);
    }
}

