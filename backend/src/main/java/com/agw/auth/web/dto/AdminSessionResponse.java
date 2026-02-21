package com.agw.auth.web.dto;

import java.time.Instant;

public record AdminSessionResponse(String username, Instant issuedAt) {
}
