package com.agw.auth.config;

import com.agw.auth.websocket.AppWsAuthHandshakeInterceptor;
import com.agw.auth.websocket.AppWsHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AppWebSocketConfig implements WebSocketConfigurer {

    private final AppWsHandler appWsHandler;
    private final AppWsAuthHandshakeInterceptor appWsAuthHandshakeInterceptor;

    public AppWebSocketConfig(
        AppWsHandler appWsHandler,
        AppWsAuthHandshakeInterceptor appWsAuthHandshakeInterceptor
    ) {
        this.appWsHandler = appWsHandler;
        this.appWsAuthHandshakeInterceptor = appWsAuthHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(appWsHandler, "/api/app/ws")
            .addInterceptors(appWsAuthHandshakeInterceptor)
            .setAllowedOriginPatterns("*");
    }
}

