package com.app.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AdminAppTokenRefreshRequest(
    @NotBlank String deviceToken,
    @Positive Integer accessTtlSeconds
) {
}
