package com.app.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserUpdateRequest(
    @NotBlank String displayName,
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status must be ACTIVE or DISABLED") String status
) {
}
