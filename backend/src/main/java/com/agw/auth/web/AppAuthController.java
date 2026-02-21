package com.agw.auth.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.agw.auth.domain.DeviceRecord;
import com.agw.auth.security.AppPrincipal;
import com.agw.auth.service.AppAuthService;
import com.agw.auth.service.AppAuthService.LoginResult;
import com.agw.auth.service.AppAuthService.RefreshResult;
import com.agw.auth.service.JwkKeyService;
import com.agw.auth.web.dto.AppDeviceResponse;
import com.agw.auth.web.dto.AppLoginRequest;
import com.agw.auth.web.dto.AppLoginResponse;
import com.agw.auth.web.dto.AppMeResponse;
import com.agw.auth.web.dto.AppRefreshRequest;
import com.agw.auth.web.dto.AppRefreshResponse;
import com.agw.auth.web.dto.DeviceRenameRequest;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AppAuthController {

    private final AppAuthService appAuthService;
    private final JwkKeyService jwkKeyService;

    public AppAuthController(AppAuthService appAuthService, JwkKeyService jwkKeyService) {
        this.appAuthService = appAuthService;
        this.jwkKeyService = jwkKeyService;
    }

    @PostMapping("/login")
    public AppLoginResponse login(@Valid @RequestBody AppLoginRequest request) {
        LoginResult result = appAuthService.login(
                request.masterPassword(),
                request.deviceName(),
                request.accessTtlSeconds()
            )
            .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        return new AppLoginResponse(
            result.username(),
            result.device().deviceId(),
            result.device().deviceName(),
            result.accessToken(),
            result.accessExpireAt(),
            result.deviceToken()
        );
    }

    @PostMapping("/refresh")
    public AppRefreshResponse refresh(@Valid @RequestBody AppRefreshRequest request) {
        RefreshResult result = appAuthService.refresh(request.deviceToken(), request.accessTtlSeconds())
            .orElseThrow(() -> new IllegalArgumentException("invalid device token"));
        return new AppRefreshResponse(
            result.device().deviceId(),
            result.accessToken(),
            result.accessExpireAt(),
            result.deviceToken()
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        AppPrincipal principal = requirePrincipal(authentication);
        appAuthService.logout(principal);
    }

    @GetMapping("/me")
    public AppMeResponse me(Authentication authentication) {
        AppPrincipal principal = requirePrincipal(authentication);
        return new AppMeResponse(principal.username(), principal.deviceId(), principal.issuedAt());
    }

    @GetMapping("/devices")
    public List<AppDeviceResponse> devices() {
        return appAuthService.listDevices().stream()
            .map(AppAuthController::toDeviceResponse)
            .toList();
    }

    @PatchMapping("/devices/{deviceId}")
    public AppDeviceResponse renameDevice(
        @PathVariable UUID deviceId,
        @Valid @RequestBody DeviceRenameRequest request
    ) {
        appAuthService.renameDevice(deviceId, request.deviceName());
        DeviceRecord updated = appAuthService.listDevices().stream()
            .filter(item -> item.deviceId().equals(deviceId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("device not found"));
        return toDeviceResponse(updated);
    }

    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Void> revokeDevice(@PathVariable UUID deviceId) {
        appAuthService.revokeDevice(deviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        RSAKey key = jwkKeyService.loadOrCreate();
        return Map.of("keys", List.of(key.toPublicJWK().toJSONObject()));
    }

    private AppPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }
        return principal;
    }

    private static AppDeviceResponse toDeviceResponse(DeviceRecord device) {
        return new AppDeviceResponse(
            device.deviceId(),
            device.deviceName(),
            device.status(),
            device.lastSeenAt(),
            device.revokedAt(),
            device.createAt(),
            device.updateAt()
        );
    }
}
