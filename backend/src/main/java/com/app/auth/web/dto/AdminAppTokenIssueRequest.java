package com.app.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AdminAppTokenIssueRequest(
    @NotBlank String masterPassword,
    @NotBlank String deviceName,
    @Positive Integer accessTtlSeconds
) {
}
