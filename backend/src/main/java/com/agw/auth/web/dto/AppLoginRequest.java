package com.agw.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AppLoginRequest(
    @NotBlank String masterPassword,
    @NotBlank String deviceName,
    @Positive Integer accessTtlSeconds
) {
}
