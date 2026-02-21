package com.app.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import com.app.auth.service.AdminSessionService;
import com.app.auth.service.AdminSessionService.AdminSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminApiInterceptor implements HandlerInterceptor {

    public static final String ADMIN_SESSION_ATTR = "ADMIN_SESSION";
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
        "/admin/api/session/login",
        "/admin/api/bcrypt/generate"
    );

    private final AdminSessionService adminSessionService;

    public AdminApiInterceptor(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (PUBLIC_ENDPOINTS.contains(path)) {
            return true;
        }

        String sessionId = resolveSessionId(request);
        AdminSession session = adminSessionService.getSession(sessionId).orElse(null);
        if (session == null) {
            writeUnauthorized(response);
            return false;
        }

        request.setAttribute(ADMIN_SESSION_ATTR, session);
        return true;
    }

    private String resolveSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
            .filter(cookie -> AdminSessionService.COOKIE_NAME.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    }
}
