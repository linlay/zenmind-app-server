package com.agw.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record InboxMessage(
    UUID messageId,
    String title,
    String content,
    String type,
    String payloadJson,
    String sender,
    Instant readAt,
    Instant createAt,
    Instant updateAt
) {

    public boolean isRead() {
        return readAt != null;
    }
}

