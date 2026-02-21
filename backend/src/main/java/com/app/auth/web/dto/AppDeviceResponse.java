package com.app.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AppDeviceResponse(
    UUID deviceId,
    String deviceName,
    String status,
    Instant lastSeenAt,
    Instant revokedAt,
    Instant createAt,
    Instant updateAt
) {
}

