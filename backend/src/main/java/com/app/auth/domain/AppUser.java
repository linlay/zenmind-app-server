package com.app.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record AppUser(
    UUID userId,
    String username,
    String passwordBcrypt,
    String displayName,
    String status,
    Instant createAt,
    Instant updateAt
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
