package com.app.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record BcryptGenerateRequest(
    @NotBlank String password
) {
}
