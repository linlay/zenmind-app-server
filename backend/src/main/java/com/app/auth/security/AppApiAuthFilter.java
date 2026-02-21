package com.app.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.app.auth.service.AppAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AppApiAuthFilter extends OncePerRequestFilter {

    public static final String APP_PRINCIPAL_ATTR = "APP_PRINCIPAL";

    private static final String AUTH_PREFIX = "Bearer ";

    private final AppAuthService appAuthService;

    public AppApiAuthFilter(AppAuthService appAuthService) {
        this.appAuthService = appAuthService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/auth/") && !path.startsWith("/api/app/")) {
            return true;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if ("/api/auth/login".equals(path) || "/api/auth/refresh".equals(path) || "/api/auth/jwks".equals(path)) {
            return true;
        }

        if ("/api/app/ws".equals(path)) {
            return true;
        }

        return "/api/app/internal/chat-events".equals(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveBearerToken(request);
        AppPrincipal principal = appAuthService.authenticateAccessToken(token).orElse(null);
        if (principal == null) {
            writeUnauthorized(response);
            return;
        }

        request.setAttribute(APP_PRINCIPAL_ATTR, principal);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_APP_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(AUTH_PREFIX)) {
            return null;
        }
        String token = authorization.substring(AUTH_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    }
}
