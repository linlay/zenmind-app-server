package com.app.auth.web.dto;

public record InternalChatEventAckResponse(
    boolean accepted,
    boolean duplicate
) {
}

