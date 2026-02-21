package com.app.auth.web.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record AdminInboxSendRequest(
    @NotBlank String title,
    @NotBlank String content,
    String type,
    Map<String, Object> payload
) {
}

