package com.app.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PublicKeyGenerateRequest(
    @NotBlank String e,
    @NotBlank String n
) {
}
