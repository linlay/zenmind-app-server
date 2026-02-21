package com.app.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceRenameRequest(
    @NotBlank String deviceName
) {
}

