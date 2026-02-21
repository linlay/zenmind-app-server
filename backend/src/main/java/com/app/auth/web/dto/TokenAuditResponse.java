package com.app.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

public record TokenAuditResponse(
    UUID tokenId,
    String source,
    String token,
    String tokenSha256,
    String username,
    UUID deviceId,
    String deviceName,
    String clientId,
    String authorizationId,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt,
    String status
) {
}
