package com.agw.auth.service;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatEventDedupService {

    private final JdbcTemplate jdbcTemplate;

    public ChatEventDedupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean markIfFirst(String chatId, String runId) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(runId)) {
            return false;
        }
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO CHAT_EVENT_DEDUP_(CHAT_ID_, RUN_ID_, CREATE_AT_)
                    VALUES (?, ?, ?)
                """,
                chatId.trim(),
                runId.trim(),
                Timestamp.from(Instant.now())
            );
            return true;
        } catch (DataAccessException ex) {
            if (isDuplicateConstraint(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private boolean isDuplicateConstraint(DataAccessException ex) {
        String message = ex.getMessage();
        return StringUtils.hasText(message)
            && message.toLowerCase().contains("unique constraint failed");
    }
}
