package com.app.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.app.auth.domain.TokenAuditRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TokenAuditService {

    public static final String SOURCE_APP_ACCESS = "APP_ACCESS";
    public static final String SOURCE_OAUTH_ACCESS = "OAUTH_ACCESS";
    public static final String SOURCE_OAUTH_REFRESH = "OAUTH_REFRESH";

    public static final String STATUS_ALL = "ALL";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REVOKED = "REVOKED";

    private static final Duration RETENTION = Duration.ofDays(30);
    private static final Set<String> DEFAULT_SOURCES = Set.of(
        SOURCE_APP_ACCESS,
        SOURCE_OAUTH_ACCESS,
        SOURCE_OAUTH_REFRESH
    );

    private static final RowMapper<TokenAuditRecord> TOKEN_AUDIT_ROW_MAPPER = TokenAuditService::mapTokenAudit;

    private final JdbcTemplate jdbcTemplate;

    public TokenAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordAppAccessToken(
        String token,
        String username,
        UUID deviceId,
        String deviceName,
        Instant issuedAt,
        Instant expiresAt
    ) {
        upsertToken(
            SOURCE_APP_ACCESS,
            token,
            username,
            deviceId,
            deviceName,
            null,
            null,
            issuedAt,
            expiresAt
        );
    }

    public void recordOAuthAccessToken(
        String token,
        String username,
        String clientId,
        String authorizationId,
        Instant issuedAt,
        Instant expiresAt
    ) {
        upsertToken(
            SOURCE_OAUTH_ACCESS,
            token,
            username,
            null,
            null,
            clientId,
            authorizationId,
            issuedAt,
            expiresAt
        );
    }

    public void recordOAuthRefreshToken(
        String token,
        String username,
        String clientId,
        String authorizationId,
        Instant issuedAt,
        Instant expiresAt
    ) {
        upsertToken(
            SOURCE_OAUTH_REFRESH,
            token,
            username,
            null,
            null,
            clientId,
            authorizationId,
            issuedAt,
            expiresAt
        );
    }

    public void markRevokedByDeviceId(UUID deviceId) {
        if (deviceId == null) {
            return;
        }
        cleanupExpiredRetention();
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                UPDATE TOKEN_AUDIT_
                SET REVOKED_AT_ = COALESCE(REVOKED_AT_, ?), UPDATE_AT_ = ?
                WHERE DEVICE_ID_ = ? AND REVOKED_AT_ IS NULL
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            deviceId.toString()
        );
    }

    public void markRevokedByAuthorizationId(String authorizationId) {
        if (!StringUtils.hasText(authorizationId)) {
            return;
        }
        cleanupExpiredRetention();
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                UPDATE TOKEN_AUDIT_
                SET REVOKED_AT_ = COALESCE(REVOKED_AT_, ?), UPDATE_AT_ = ?
                WHERE AUTHORIZATION_ID_ = ? AND REVOKED_AT_ IS NULL
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            authorizationId.trim()
        );
    }

    public List<TokenAuditRecord> listTokens(Set<String> requestedSources, String requestedStatus, int requestedLimit) {
        cleanupExpiredRetention();
        Set<String> sources = normalizeSources(requestedSources);
        String status = normalizeStatus(requestedStatus);
        int limit = Math.max(1, Math.min(requestedLimit, 200));

        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        Instant now = Instant.now();

        if (!sources.isEmpty()) {
            String placeholders = sources.stream().map(item -> "?").collect(Collectors.joining(", "));
            where.add("SOURCE_ IN (" + placeholders + ")");
            args.addAll(sources);
        }

        switch (status) {
            case STATUS_ACTIVE -> {
                where.add("REVOKED_AT_ IS NULL");
                where.add("(EXPIRES_AT_ IS NULL OR EXPIRES_AT_ > ?)");
                args.add(Timestamp.from(now));
            }
            case STATUS_EXPIRED -> {
                where.add("REVOKED_AT_ IS NULL");
                where.add("EXPIRES_AT_ IS NOT NULL");
                where.add("EXPIRES_AT_ <= ?");
                args.add(Timestamp.from(now));
            }
            case STATUS_REVOKED -> where.add("REVOKED_AT_ IS NOT NULL");
            case STATUS_ALL -> {
                // no-op
            }
            default -> throw new IllegalArgumentException("unsupported status: " + status);
        }

        String sql = new StringBuilder()
            .append("""
                SELECT TOKEN_ID_, SOURCE_, TOKEN_VALUE_, TOKEN_SHA256_, USERNAME_, DEVICE_ID_, DEVICE_NAME_, CLIENT_ID_, AUTHORIZATION_ID_,
                       ISSUED_AT_, EXPIRES_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_
                FROM TOKEN_AUDIT_
            """)
            .append(where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
            .append(" ORDER BY ISSUED_AT_ DESC, CREATE_AT_ DESC LIMIT ?")
            .toString();
        args.add(limit);
        return jdbcTemplate.query(sql, TOKEN_AUDIT_ROW_MAPPER, args.toArray());
    }

    public String resolveStatus(TokenAuditRecord record) {
        if (record == null) {
            return STATUS_EXPIRED;
        }
        if (record.revokedAt() != null) {
            return STATUS_REVOKED;
        }
        if (record.expiresAt() != null && !record.expiresAt().isAfter(Instant.now())) {
            return STATUS_EXPIRED;
        }
        return STATUS_ACTIVE;
    }

    public static Set<String> parseSources(String csv) {
        if (!StringUtils.hasText(csv)) {
            return DEFAULT_SOURCES;
        }
        Set<String> values = new LinkedHashSet<>();
        for (String raw : csv.split(",")) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            values.add(raw.trim().toUpperCase());
        }
        return values;
    }

    private void upsertToken(
        String source,
        String token,
        String username,
        UUID deviceId,
        String deviceName,
        String clientId,
        String authorizationId,
        Instant issuedAt,
        Instant expiresAt
    ) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(source) || issuedAt == null) {
            return;
        }
        cleanupExpiredRetention();
        Instant now = Instant.now();
        String normalizedToken = token.trim();
        String tokenSha256 = sha256(normalizedToken);
        String normalizedSource = source.trim().toUpperCase();
        jdbcTemplate.update(
            """
                INSERT INTO TOKEN_AUDIT_ (
                    TOKEN_ID_, SOURCE_, TOKEN_VALUE_, TOKEN_SHA256_, USERNAME_, DEVICE_ID_, DEVICE_NAME_, CLIENT_ID_, AUTHORIZATION_ID_,
                    ISSUED_AT_, EXPIRES_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                ON CONFLICT(TOKEN_SHA256_) DO UPDATE SET
                    SOURCE_ = excluded.SOURCE_,
                    TOKEN_VALUE_ = excluded.TOKEN_VALUE_,
                    USERNAME_ = excluded.USERNAME_,
                    DEVICE_ID_ = excluded.DEVICE_ID_,
                    DEVICE_NAME_ = excluded.DEVICE_NAME_,
                    CLIENT_ID_ = excluded.CLIENT_ID_,
                    AUTHORIZATION_ID_ = excluded.AUTHORIZATION_ID_,
                    ISSUED_AT_ = excluded.ISSUED_AT_,
                    EXPIRES_AT_ = excluded.EXPIRES_AT_,
                    UPDATE_AT_ = excluded.UPDATE_AT_
            """,
            UUID.randomUUID().toString(),
            normalizedSource,
            normalizedToken,
            tokenSha256,
            trimToNull(username),
            uuidToText(deviceId),
            trimToNull(deviceName),
            trimToNull(clientId),
            trimToNull(authorizationId),
            Timestamp.from(issuedAt),
            expiresAt == null ? null : Timestamp.from(expiresAt),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private void cleanupExpiredRetention() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jdbcTemplate.update("DELETE FROM TOKEN_AUDIT_ WHERE ISSUED_AT_ < ?", Timestamp.from(cutoff));
    }

    private static Set<String> normalizeSources(Set<String> requestedSources) {
        if (requestedSources == null || requestedSources.isEmpty()) {
            return DEFAULT_SOURCES;
        }
        Set<String> values = requestedSources.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(String::toUpperCase)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        values.retainAll(DEFAULT_SOURCES);
        return values.isEmpty() ? DEFAULT_SOURCES : values;
    }

    private static String normalizeStatus(String requestedStatus) {
        if (!StringUtils.hasText(requestedStatus)) {
            return STATUS_ALL;
        }
        String status = requestedStatus.trim().toUpperCase();
        return switch (status) {
            case STATUS_ALL, STATUS_ACTIVE, STATUS_EXPIRED, STATUS_REVOKED -> status;
            default -> throw new IllegalArgumentException("status must be one of: ALL, ACTIVE, EXPIRED, REVOKED");
        };
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to hash token", ex);
        }
    }

    private static String uuidToText(UUID value) {
        return value == null ? null : value.toString();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static TokenAuditRecord mapTokenAudit(ResultSet rs, int rowNum) throws SQLException {
        Timestamp expiresAt = rs.getTimestamp("EXPIRES_AT_");
        Timestamp revokedAt = rs.getTimestamp("REVOKED_AT_");
        return new TokenAuditRecord(
            UUID.fromString(rs.getString("TOKEN_ID_")),
            rs.getString("SOURCE_"),
            rs.getString("TOKEN_VALUE_"),
            rs.getString("TOKEN_SHA256_"),
            rs.getString("USERNAME_"),
            toUuid(rs.getString("DEVICE_ID_")),
            rs.getString("DEVICE_NAME_"),
            rs.getString("CLIENT_ID_"),
            rs.getString("AUTHORIZATION_ID_"),
            rs.getTimestamp("ISSUED_AT_").toInstant(),
            expiresAt == null ? null : expiresAt.toInstant(),
            revokedAt == null ? null : revokedAt.toInstant(),
            rs.getTimestamp("CREATE_AT_").toInstant(),
            rs.getTimestamp("UPDATE_AT_").toInstant()
        );
    }

    private static UUID toUuid(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return UUID.fromString(text);
    }
}
