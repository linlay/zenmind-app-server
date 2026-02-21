package com.app.auth.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.app.auth.domain.DeviceRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceService {

    private static final RowMapper<DeviceRecord> DEVICE_ROW_MAPPER = DeviceService::mapDevice;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DeviceService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public DeviceRecord createDevice(String deviceName, String rawDeviceToken) {
        Instant now = Instant.now();
        UUID deviceId = UUID.randomUUID();
        String tokenBcrypt = passwordEncoder.encode(rawDeviceToken);
        jdbcTemplate.update(
            """
                INSERT INTO DEVICE_ (
                  DEVICE_ID_, DEVICE_NAME_, DEVICE_TOKEN_BCRYPT_, STATUS_, LAST_SEEN_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_
                ) VALUES (?, ?, ?, 'ACTIVE', ?, NULL, ?, ?)
            """,
            deviceId.toString(),
            normalizeDeviceName(deviceName),
            tokenBcrypt,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return findById(deviceId).orElseThrow();
    }

    public Optional<DeviceRecord> findById(UUID deviceId) {
        List<DeviceRecord> list = jdbcTemplate.query(
            """
                SELECT DEVICE_ID_, DEVICE_NAME_, DEVICE_TOKEN_BCRYPT_, STATUS_, LAST_SEEN_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_
                FROM DEVICE_
                WHERE DEVICE_ID_ = ?
            """,
            DEVICE_ROW_MAPPER,
            deviceId.toString()
        );
        return list.stream().findFirst();
    }

    public Optional<DeviceRecord> findActiveByToken(String rawDeviceToken) {
        if (!StringUtils.hasText(rawDeviceToken)) {
            return Optional.empty();
        }

        List<DeviceRecord> list = jdbcTemplate.query(
            """
                SELECT DEVICE_ID_, DEVICE_NAME_, DEVICE_TOKEN_BCRYPT_, STATUS_, LAST_SEEN_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_
                FROM DEVICE_
                WHERE STATUS_ = 'ACTIVE'
                ORDER BY UPDATE_AT_ DESC
            """,
            DEVICE_ROW_MAPPER
        );

        for (DeviceRecord item : list) {
            if (passwordEncoder.matches(rawDeviceToken, item.deviceTokenBcrypt())) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public List<DeviceRecord> listDevices() {
        return jdbcTemplate.query(
            """
                SELECT DEVICE_ID_, DEVICE_NAME_, DEVICE_TOKEN_BCRYPT_, STATUS_, LAST_SEEN_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_
                FROM DEVICE_
                ORDER BY UPDATE_AT_ DESC
            """,
            DEVICE_ROW_MAPPER
        );
    }

    public boolean isActive(UUID deviceId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM DEVICE_ WHERE DEVICE_ID_ = ? AND STATUS_ = 'ACTIVE'",
            Integer.class,
            deviceId.toString()
        );
        return count != null && count > 0;
    }

    public void touch(UUID deviceId) {
        jdbcTemplate.update(
            """
                UPDATE DEVICE_
                SET LAST_SEEN_AT_ = ?, UPDATE_AT_ = ?
                WHERE DEVICE_ID_ = ? AND STATUS_ = 'ACTIVE'
            """,
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now()),
            deviceId.toString()
        );
    }

    public void rename(UUID deviceId, String deviceName) {
        jdbcTemplate.update(
            """
                UPDATE DEVICE_
                SET DEVICE_NAME_ = ?, UPDATE_AT_ = ?
                WHERE DEVICE_ID_ = ?
            """,
            normalizeDeviceName(deviceName),
            Timestamp.from(Instant.now()),
            deviceId.toString()
        );
    }

    public void revoke(UUID deviceId) {
        jdbcTemplate.update(
            """
                UPDATE DEVICE_
                SET STATUS_ = 'REVOKED', REVOKED_AT_ = ?, UPDATE_AT_ = ?
                WHERE DEVICE_ID_ = ? AND STATUS_ = 'ACTIVE'
            """,
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now()),
            deviceId.toString()
        );
    }

    public void rotateToken(UUID deviceId, String rawDeviceToken) {
        jdbcTemplate.update(
            """
                UPDATE DEVICE_
                SET DEVICE_TOKEN_BCRYPT_ = ?, LAST_SEEN_AT_ = ?, UPDATE_AT_ = ?
                WHERE DEVICE_ID_ = ? AND STATUS_ = 'ACTIVE'
            """,
            passwordEncoder.encode(rawDeviceToken),
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now()),
            deviceId.toString()
        );
    }

    private String normalizeDeviceName(String deviceName) {
        String value = StringUtils.hasText(deviceName) ? deviceName.trim() : "Unknown Device";
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static DeviceRecord mapDevice(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lastSeenAt = rs.getTimestamp("LAST_SEEN_AT_");
        Timestamp revokedAt = rs.getTimestamp("REVOKED_AT_");
        return new DeviceRecord(
            UUID.fromString(rs.getString("DEVICE_ID_")),
            rs.getString("DEVICE_NAME_"),
            rs.getString("DEVICE_TOKEN_BCRYPT_"),
            rs.getString("STATUS_"),
            lastSeenAt == null ? null : lastSeenAt.toInstant(),
            revokedAt == null ? null : revokedAt.toInstant(),
            rs.getTimestamp("CREATE_AT_").toInstant(),
            rs.getTimestamp("UPDATE_AT_").toInstant()
        );
    }
}

