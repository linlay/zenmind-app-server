package com.agw.auth.websocket;

import java.util.Map;

import com.agw.auth.security.AppPrincipal;
import com.agw.auth.service.AppAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class AppWsAuthHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_APP_PRINCIPAL = "APP_PRINCIPAL";
    private static final String AUTH_PREFIX = "Bearer ";

    private final AppAuthService appAuthService;

    public AppWsAuthHandshakeInterceptor(AppAuthService appAuthService) {
        this.appAuthService = appAuthService;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        String token = resolveToken(request);
        AppPrincipal principal = appAuthService.authenticateAccessToken(token).orElse(null);
        if (principal == null) {
            if (response instanceof ServletServerHttpResponse servletResponse) {
                servletResponse.getServletResponse().setStatus(HttpStatus.UNAUTHORIZED.value());
            }
            return false;
        }
        attributes.put(ATTR_APP_PRINCIPAL, principal);
        return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
        // no-op
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith(AUTH_PREFIX)) {
            String token = authorization.substring(AUTH_PREFIX.length()).trim();
            if (StringUtils.hasText(token)) {
                return token;
            }
        }

        if (request instanceof ServletServerHttpRequest servletRequest) {
            String queryToken = servletRequest.getServletRequest().getParameter("access_token");
            return StringUtils.hasText(queryToken) ? queryToken.trim() : null;
        }

        return null;
    }
}

