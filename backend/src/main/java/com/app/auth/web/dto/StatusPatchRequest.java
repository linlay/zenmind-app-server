package com.app.auth.web.dto;

import jakarta.validation.constraints.Pattern;

public record StatusPatchRequest(
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status must be ACTIVE or DISABLED")
    String status
) {
}
