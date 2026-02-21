package com.agw.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AppRefreshRequest(
    @NotBlank String deviceToken,
    @Positive Integer accessTtlSeconds
) {
}
