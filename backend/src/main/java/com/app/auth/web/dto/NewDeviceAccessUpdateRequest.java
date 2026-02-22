package com.app.auth.web.dto;

import jakarta.validation.constraints.NotNull;

public record NewDeviceAccessUpdateRequest(
    @NotNull Boolean allowNewDeviceLogin
) {
}

