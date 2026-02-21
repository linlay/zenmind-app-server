package com.app.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AppLoginResponse(
    String username,
    UUID deviceId,
    String deviceName,
    String accessToken,
    Instant accessTokenExpireAt,
    String deviceToken
) {
}

