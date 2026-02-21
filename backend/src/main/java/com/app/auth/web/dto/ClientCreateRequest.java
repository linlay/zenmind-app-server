package com.app.auth.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ClientCreateRequest(
    @NotBlank String clientId,
    @NotBlank String clientName,
    String clientSecret,
    @NotEmpty List<String> grantTypes,
    List<String> redirectUris,
    @NotEmpty List<String> scopes,
    Boolean requirePkce,
    String status
) {
}
