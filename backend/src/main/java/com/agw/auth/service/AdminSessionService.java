package com.agw.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.agw.auth.config.AuthProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminSessionService {

    public static final String COOKIE_NAME = "ADMIN_SESSION";

    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final Cache<String, AdminSession> sessionCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(8))
        .maximumSize(10_000)
        .build();

    public AdminSessionService(AuthProperties authProperties, PasswordEncoder passwordEncoder) {
        this.authProperties = authProperties;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<AdminSession> login(String username, String rawPassword) {
        boolean match = authProperties.getAdmin().getUsername().equals(username)
            && passwordEncoder.matches(rawPassword, authProperties.getAdmin().getPasswordBcrypt());

        if (!match) {
            return Optional.empty();
        }

        String sessionId = UUID.randomUUID().toString();
        AdminSession session = new AdminSession(sessionId, username, Instant.now());
        sessionCache.put(sessionId, session);
        return Optional.of(session);
    }

    public Optional<AdminSession> getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionCache.getIfPresent(sessionId));
    }

    public void logout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionCache.invalidate(sessionId);
    }

    public record AdminSession(String sessionId, String username, Instant issuedAt) {
    }
}
