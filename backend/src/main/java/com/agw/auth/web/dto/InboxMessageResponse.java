package com.agw.auth.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InboxMessageResponse(
    UUID messageId,
    String title,
    String content,
    String type,
    String sender,
    Map<String, Object> payload,
    boolean read,
    Instant readAt,
    Instant createAt,
    Instant updateAt
) {
}

