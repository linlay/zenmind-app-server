package com.agw.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserCreateRequest(
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank String displayName,
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status must be ACTIVE or DISABLED") String status
) {
}
