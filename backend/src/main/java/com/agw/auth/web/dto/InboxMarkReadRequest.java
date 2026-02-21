package com.agw.auth.web.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

public record InboxMarkReadRequest(
    @NotEmpty List<UUID> messageIds
) {
}

