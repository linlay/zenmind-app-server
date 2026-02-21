package com.agw.auth.web.dto;

public record InternalChatEventAckResponse(
    boolean accepted,
    boolean duplicate
) {
}

