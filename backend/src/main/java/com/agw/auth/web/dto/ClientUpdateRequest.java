package com.agw.auth.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ClientUpdateRequest(
    @NotBlank String clientName,
    List<String> grantTypes,
    List<String> redirectUris,
    @NotEmpty List<String> scopes,
    Boolean requirePkce,
    String status
) {
}
