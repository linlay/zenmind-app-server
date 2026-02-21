package com.agw.auth.web.dto;

import java.time.Instant;
import java.util.List;

public record ClientResponse(
    String id,
    String clientId,
    String clientName,
    List<String> grantTypes,
    List<String> redirectUris,
    List<String> scopes,
    boolean requirePkce,
    String status,
    Instant createAt,
    Instant updateAt
) {
}
