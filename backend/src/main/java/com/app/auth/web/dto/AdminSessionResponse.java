package com.app.auth.web.dto;

import java.time.Instant;

public record AdminSessionResponse(String username, Instant issuedAt) {
}
