package com.agw.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AppMeResponse(
    String username,
    UUID deviceId,
    Instant issuedAt
) {
}

