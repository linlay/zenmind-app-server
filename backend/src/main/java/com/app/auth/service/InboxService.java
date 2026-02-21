package com.app.auth.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.app.auth.domain.InboxMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InboxService {

    private static final RowMapper<InboxMessage> INBOX_ROW_MAPPER = InboxService::mapInbox;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public InboxMessage createMessage(String title, String content, String type, Map<String, Object> payload, String sender) {
        String normalizedTitle = normalizeText(title, 120, "title");
        String normalizedContent = normalizeText(content, 4000, "content");
        String normalizedType = normalizeType(type);
        String normalizedSender = StringUtils.hasText(sender) ? sender.trim() : "SYSTEM";
        Instant now = Instant.now();
        UUID messageId = UUID.randomUUID();

        jdbcTemplate.update(
            """
                INSERT INTO INBOX_MESSAGE_ (
                  MESSAGE_ID_, TITLE_, CONTENT_, TYPE_, PAYLOAD_JSON_, SENDER_, READ_AT_, CREATE_AT_, UPDATE_AT_
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?)
            """,
            messageId.toString(),
            normalizedTitle,
            normalizedContent,
            normalizedType,
            toJson(payload),
            normalizedSender,
            Timestamp.from(now),
            Timestamp.from(now)
        );

        return findById(messageId).orElseThrow();
    }

    public List<InboxMessage> listMessages(boolean unreadOnly, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 200));
        if (unreadOnly) {
            return jdbcTemplate.query(
                """
                    SELECT MESSAGE_ID_, TITLE_, CONTENT_, TYPE_, PAYLOAD_JSON_, SENDER_, READ_AT_, CREATE_AT_, UPDATE_AT_
                    FROM INBOX_MESSAGE_
                    WHERE READ_AT_ IS NULL
                    ORDER BY CREATE_AT_ DESC
                    LIMIT ?
                """,
                INBOX_ROW_MAPPER,
                normalizedLimit
            );
        }

        return jdbcTemplate.query(
            """
                SELECT MESSAGE_ID_, TITLE_, CONTENT_, TYPE_, PAYLOAD_JSON_, SENDER_, READ_AT_, CREATE_AT_, UPDATE_AT_
                FROM INBOX_MESSAGE_
                ORDER BY CREATE_AT_ DESC
                LIMIT ?
            """,
            INBOX_ROW_MAPPER,
            normalizedLimit
        );
    }

    public long unreadCount() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INBOX_MESSAGE_ WHERE READ_AT_ IS NULL",
            Long.class
        );
        return count == null ? 0L : count;
    }

    public void markRead(List<UUID> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        List<String> ids = messageIds.stream().map(UUID::toString).toList();
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = """
            UPDATE INBOX_MESSAGE_
            SET READ_AT_ = COALESCE(READ_AT_, ?), UPDATE_AT_ = ?
            WHERE MESSAGE_ID_ IN (%s)
        """.formatted(placeholders);

        List<Object> params = new java.util.ArrayList<>();
        params.add(Timestamp.from(Instant.now()));
        params.add(Timestamp.from(Instant.now()));
        params.addAll(ids);
        jdbcTemplate.update(sql, params.toArray());
    }

    public void markAllRead() {
        jdbcTemplate.update(
            """
                UPDATE INBOX_MESSAGE_
                SET READ_AT_ = COALESCE(READ_AT_, ?), UPDATE_AT_ = ?
                WHERE READ_AT_ IS NULL
            """,
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now())
        );
    }

    public Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(payloadJson, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private java.util.Optional<InboxMessage> findById(UUID messageId) {
        List<InboxMessage> list = jdbcTemplate.query(
            """
                SELECT MESSAGE_ID_, TITLE_, CONTENT_, TYPE_, PAYLOAD_JSON_, SENDER_, READ_AT_, CREATE_AT_, UPDATE_AT_
                FROM INBOX_MESSAGE_
                WHERE MESSAGE_ID_ = ?
            """,
            INBOX_ROW_MAPPER,
            messageId.toString()
        );
        return list.stream().findFirst();
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("payload is not serializable");
        }
    }

    private String normalizeText(String text, int maxLength, String fieldName) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = text.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "INFO";
        }
        String normalized = type.trim().toUpperCase();
        return switch (normalized) {
            case "INFO", "WARN", "ERROR", "SYSTEM" -> normalized;
            default -> "INFO";
        };
    }

    private static InboxMessage mapInbox(ResultSet rs, int rowNum) throws SQLException {
        Timestamp readAt = rs.getTimestamp("READ_AT_");
        return new InboxMessage(
            UUID.fromString(rs.getString("MESSAGE_ID_")),
            rs.getString("TITLE_"),
            rs.getString("CONTENT_"),
            rs.getString("TYPE_"),
            rs.getString("PAYLOAD_JSON_"),
            rs.getString("SENDER_"),
            readAt == null ? null : readAt.toInstant(),
            rs.getTimestamp("CREATE_AT_").toInstant(),
            rs.getTimestamp("UPDATE_AT_").toInstant()
        );
    }
}

