package com.agw.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record DeviceRecord(
    UUID deviceId,
    String deviceName,
    String deviceTokenBcrypt,
    String status,
    Instant lastSeenAt,
    Instant revokedAt,
    Instant createAt,
    Instant updateAt
) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}

