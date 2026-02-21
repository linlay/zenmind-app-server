package com.agw.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.agw.auth.config.AuthProperties;
import com.agw.auth.domain.DeviceRecord;
import com.agw.auth.security.AppPrincipal;
import com.agw.auth.service.AppTokenService.IssuedAccessToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AppAuthService {

    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final DeviceService deviceService;
    private final AppTokenService appTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppAuthService(
        AuthProperties authProperties,
        PasswordEncoder passwordEncoder,
        DeviceService deviceService,
        AppTokenService appTokenService
    ) {
        this.authProperties = authProperties;
        this.passwordEncoder = passwordEncoder;
        this.deviceService = deviceService;
        this.appTokenService = appTokenService;
    }

    public Optional<LoginResult> login(String masterPassword, String deviceName, Integer accessTtlSeconds) {
        if (!verifyMasterPassword(masterPassword)) {
            return Optional.empty();
        }

        String deviceToken = generateDeviceToken();
        DeviceRecord device = deviceService.createDevice(deviceName, deviceToken);
        Duration accessTtl = resolveAccessTtl(accessTtlSeconds);
        IssuedAccessToken accessToken = appTokenService.issueAccessToken(
            authProperties.getApp().getUsername(),
            device.deviceId(),
            accessTtl
        );
        return Optional.of(new LoginResult(
            authProperties.getApp().getUsername(),
            device,
            deviceToken,
            accessToken.token(),
            accessToken.expireAt()
        ));
    }

    public Optional<RefreshResult> refresh(String rawDeviceToken, Integer accessTtlSeconds) {
        DeviceRecord device = deviceService.findActiveByToken(rawDeviceToken).orElse(null);
        if (device == null) {
            return Optional.empty();
        }

        String nextDeviceToken = rawDeviceToken;
        if (authProperties.getApp().isRotateDeviceToken()) {
            nextDeviceToken = generateDeviceToken();
            deviceService.rotateToken(device.deviceId(), nextDeviceToken);
        } else {
            deviceService.touch(device.deviceId());
        }

        DeviceRecord latest = deviceService.findById(device.deviceId()).orElse(device);
        Duration accessTtl = resolveAccessTtl(accessTtlSeconds);
        IssuedAccessToken accessToken = appTokenService.issueAccessToken(
            authProperties.getApp().getUsername(),
            latest.deviceId(),
            accessTtl
        );
        return Optional.of(new RefreshResult(
            latest,
            nextDeviceToken,
            accessToken.token(),
            accessToken.expireAt()
        ));
    }

    public Optional<AppPrincipal> authenticateAccessToken(String accessToken) {
        AppPrincipal principal = appTokenService.verify(accessToken).orElse(null);
        if (principal == null) {
            return Optional.empty();
        }

        if (!deviceService.isActive(principal.deviceId())) {
            return Optional.empty();
        }

        deviceService.touch(principal.deviceId());
        return Optional.of(principal);
    }

    public void logout(AppPrincipal principal) {
        if (principal == null) {
            return;
        }
        deviceService.revoke(principal.deviceId());
    }

    public List<DeviceRecord> listDevices() {
        return deviceService.listDevices();
    }

    public void renameDevice(UUID deviceId, String deviceName) {
        if (!StringUtils.hasText(deviceName)) {
            throw new IllegalArgumentException("deviceName is required");
        }
        deviceService.rename(deviceId, deviceName);
    }

    public void revokeDevice(UUID deviceId) {
        deviceService.revoke(deviceId);
    }

    private boolean verifyMasterPassword(String masterPassword) {
        if (!StringUtils.hasText(masterPassword)) {
            return false;
        }
        String bcrypt = authProperties.getApp().getMasterPasswordBcrypt();
        return StringUtils.hasText(bcrypt) && passwordEncoder.matches(masterPassword, bcrypt);
    }

    private Duration resolveAccessTtl(Integer requestedAccessTtlSeconds) {
        Duration defaultTtl = authProperties.getApp().getAccessTtl();
        if (requestedAccessTtlSeconds == null) {
            return defaultTtl;
        }
        if (requestedAccessTtlSeconds <= 0) {
            throw new IllegalArgumentException("accessTtlSeconds must be positive");
        }
        Duration requested = Duration.ofSeconds(requestedAccessTtlSeconds.longValue());
        Duration maxAllowed = authProperties.getApp().getMaxAccessTtl();
        if (maxAllowed == null || maxAllowed.isNegative() || maxAllowed.isZero()) {
            throw new IllegalStateException("auth.app.max-access-ttl must be positive");
        }
        if (requested.compareTo(maxAllowed) > 0) {
            throw new IllegalArgumentException(
                "requested access ttl exceeds limit, max seconds=" + maxAllowed.toSeconds()
            );
        }
        return requested;
    }

    private String generateDeviceToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record LoginResult(
        String username,
        DeviceRecord device,
        String deviceToken,
        String accessToken,
        Instant accessExpireAt
    ) {
    }

    public record RefreshResult(
        DeviceRecord device,
        String deviceToken,
        String accessToken,
        Instant accessExpireAt
    ) {
    }
}
