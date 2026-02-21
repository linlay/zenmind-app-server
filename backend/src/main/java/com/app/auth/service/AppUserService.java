package com.app.auth.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.app.auth.config.CacheConfig;
import com.app.auth.domain.AppUser;
import com.app.auth.web.dto.UserCreateRequest;
import com.app.auth.web.dto.UserUpdateRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AppUserService {

    private static final RowMapper<AppUser> USER_ROW_MAPPER = AppUserService::mapUser;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Cacheable(cacheNames = CacheConfig.USER_BY_USERNAME, key = "#username")
    public Optional<AppUser> findByUsername(String username) {
        List<AppUser> list = jdbcTemplate.query(
            """
                SELECT USER_ID_, USERNAME_, PASSWORD_BCRYPT_, DISPLAY_NAME_, STATUS_, CREATE_AT_, UPDATE_AT_
                FROM APP_USER_
                WHERE USERNAME_ = ?
            """,
            USER_ROW_MAPPER,
            username
        );
        return list.stream().findFirst();
    }

    public Optional<AppUser> findByUserId(UUID userId) {
        List<AppUser> list = jdbcTemplate.query(
            """
                SELECT USER_ID_, USERNAME_, PASSWORD_BCRYPT_, DISPLAY_NAME_, STATUS_, CREATE_AT_, UPDATE_AT_
                FROM APP_USER_
                WHERE USER_ID_ = ?
            """,
            USER_ROW_MAPPER,
            userId.toString()
        );
        return list.stream().findFirst();
    }

    public List<AppUser> listUsers() {
        return jdbcTemplate.query(
            """
                SELECT USER_ID_, USERNAME_, PASSWORD_BCRYPT_, DISPLAY_NAME_, STATUS_, CREATE_AT_, UPDATE_AT_
                FROM APP_USER_
                ORDER BY CREATE_AT_ DESC
            """,
            USER_ROW_MAPPER
        );
    }

    @CacheEvict(cacheNames = CacheConfig.USER_BY_USERNAME, key = "#result.username()", condition = "#result != null")
    public AppUser createUser(UserCreateRequest request) {
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        String status = StringUtils.hasText(request.status()) ? request.status() : "ACTIVE";
        String encoded = passwordEncoder.encode(request.password());

        jdbcTemplate.update(
            """
                INSERT INTO APP_USER_ (USER_ID_, USERNAME_, PASSWORD_BCRYPT_, DISPLAY_NAME_, STATUS_, CREATE_AT_, UPDATE_AT_)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            userId.toString(),
            request.username(),
            encoded,
            request.displayName(),
            status,
            Timestamp.from(now),
            Timestamp.from(now)
        );

        return findByUserId(userId).orElseThrow();
    }

    public AppUser updateUser(UUID userId, UserUpdateRequest request) {
        AppUser current = findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("user not found"));

        jdbcTemplate.update(
            """
                UPDATE APP_USER_
                SET DISPLAY_NAME_ = ?, STATUS_ = ?, UPDATE_AT_ = ?
                WHERE USER_ID_ = ?
            """,
            request.displayName(),
            request.status(),
            Timestamp.from(Instant.now()),
            userId.toString()
        );

        evictUsernameCache(current.username());
        return findByUserId(userId).orElseThrow();
    }

    public AppUser patchStatus(UUID userId, String status) {
        AppUser current = findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("user not found"));

        jdbcTemplate.update(
            """
                UPDATE APP_USER_
                SET STATUS_ = ?, UPDATE_AT_ = ?
                WHERE USER_ID_ = ?
            """,
            status,
            Timestamp.from(Instant.now()),
            userId.toString()
        );

        evictUsernameCache(current.username());
        return findByUserId(userId).orElseThrow();
    }

    public void resetPassword(UUID userId, String rawPassword) {
        AppUser current = findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("user not found"));

        jdbcTemplate.update(
            """
                UPDATE APP_USER_
                SET PASSWORD_BCRYPT_ = ?, UPDATE_AT_ = ?
                WHERE USER_ID_ = ?
            """,
            passwordEncoder.encode(rawPassword),
            Timestamp.from(Instant.now()),
            userId.toString()
        );

        evictUsernameCache(current.username());
    }

    public void ensureBootstrapUser(String username, String passwordBcrypt, String displayName) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(passwordBcrypt)) {
            return;
        }

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM APP_USER_ WHERE USERNAME_ = ?",
            Integer.class,
            username
        );

        if (count != null && count > 0) {
            return;
        }

        Instant now = Instant.now();
        jdbcTemplate.update(
            """
                INSERT INTO APP_USER_ (USER_ID_, USERNAME_, PASSWORD_BCRYPT_, DISPLAY_NAME_, STATUS_, CREATE_AT_, UPDATE_AT_)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
            UUID.randomUUID().toString(),
            username,
            passwordBcrypt,
            StringUtils.hasText(displayName) ? displayName : username,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        evictUsernameCache(username);
    }

    @CacheEvict(cacheNames = CacheConfig.USER_BY_USERNAME, key = "#username")
    public void evictUsernameCache(String username) {
        // Cache eviction via annotation.
    }

    private static AppUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AppUser(
            UUID.fromString(rs.getString("USER_ID_")),
            rs.getString("USERNAME_"),
            rs.getString("PASSWORD_BCRYPT_"),
            rs.getString("DISPLAY_NAME_"),
            rs.getString("STATUS_"),
            rs.getTimestamp("CREATE_AT_").toInstant(),
            rs.getTimestamp("UPDATE_AT_").toInstant()
        );
    }
}
