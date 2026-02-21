package com.agw.auth.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AppRefreshResponse(
    UUID deviceId,
    String accessToken,
    Instant accessTokenExpireAt,
    String deviceToken
) {
}

