package com.agw.auth.domain;

import java.time.Instant;
import java.util.List;

public record OAuthClient(
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
