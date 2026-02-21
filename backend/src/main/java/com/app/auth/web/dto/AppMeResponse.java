package com.app.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AppMeResponse(
    String username,
    UUID deviceId,
    Instant issuedAt
) {
}

