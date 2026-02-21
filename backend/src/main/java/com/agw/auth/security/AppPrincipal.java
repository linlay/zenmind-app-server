package com.agw.auth.security;

import java.time.Instant;
import java.util.UUID;

public record AppPrincipal(
    String username,
    UUID deviceId,
    Instant issuedAt
) {
}

