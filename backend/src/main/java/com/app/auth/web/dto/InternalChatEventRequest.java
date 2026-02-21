package com.app.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record InternalChatEventRequest(
    @NotBlank String chatId,
    @NotBlank String runId,
    Long updatedAt,
    String chatName
) {
}

