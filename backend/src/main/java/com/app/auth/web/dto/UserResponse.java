package com.app.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID userId,
    String username,
    String displayName,
    String status,
    Instant createAt,
    Instant updateAt
) {
}
