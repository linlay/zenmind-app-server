package com.app.auth.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.app.auth.domain.DeviceRecord;
import com.app.auth.domain.TokenAuditRecord;
import com.app.auth.service.AppAuthService;
import com.app.auth.service.AppAuthService.LoginResult;
import com.app.auth.service.AppAuthService.RefreshResult;
import com.app.auth.service.JwkKeyService;
import com.app.auth.service.TokenAuditService;
import com.app.auth.web.dto.AdminAppTokenIssueRequest;
import com.app.auth.web.dto.AdminAppTokenRefreshRequest;
import com.app.auth.web.dto.AppDeviceResponse;
import com.app.auth.web.dto.AppLoginResponse;
import com.app.auth.web.dto.AppRefreshResponse;
import com.app.auth.web.dto.TokenAuditResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/security")
public class AdminSecurityController {

    private final AppAuthService appAuthService;
    private final JwkKeyService jwkKeyService;
    private final TokenAuditService tokenAuditService;

    public AdminSecurityController(
        AppAuthService appAuthService,
        JwkKeyService jwkKeyService,
        TokenAuditService tokenAuditService
    ) {
        this.appAuthService = appAuthService;
        this.jwkKeyService = jwkKeyService;
        this.tokenAuditService = tokenAuditService;
    }

    @PostMapping("/app-tokens/issue")
    public AppLoginResponse issueAppAccessToken(@Valid @RequestBody AdminAppTokenIssueRequest request) {
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

    @PostMapping("/app-tokens/refresh")
    public AppRefreshResponse refreshAppAccessToken(@Valid @RequestBody AdminAppTokenRefreshRequest request) {
        RefreshResult result = appAuthService.refresh(request.deviceToken(), request.accessTtlSeconds())
            .orElseThrow(() -> new IllegalArgumentException("invalid device token"));
        return new AppRefreshResponse(
            result.device().deviceId(),
            result.accessToken(),
            result.accessExpireAt(),
            result.deviceToken()
        );
    }

    @GetMapping("/app-devices")
    public List<AppDeviceResponse> appDevices() {
        return appAuthService.listDevices().stream().map(AdminSecurityController::toDeviceResponse).toList();
    }

    @PostMapping("/app-devices/{deviceId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAppDevice(@PathVariable UUID deviceId) {
        appAuthService.revokeDevice(deviceId);
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        Map<String, Object> appJwks = jwkKeyService.publicJwksResponse();
        Map<String, Object> oidcJwks = jwkKeyService.publicJwksResponse();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appJwks", appJwks);
        payload.put("oidcJwks", oidcJwks);
        return payload;
    }

    @GetMapping("/tokens")
    public List<TokenAuditResponse> listTokens(
        @RequestParam(required = false) String sources,
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(defaultValue = "200") int limit
    ) {
        Set<String> normalizedSources = TokenAuditService.parseSources(sources);
        return tokenAuditService.listTokens(normalizedSources, status, limit).stream()
            .map(record -> toTokenAuditResponse(record, tokenAuditService.resolveStatus(record)))
            .toList();
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

    private static TokenAuditResponse toTokenAuditResponse(TokenAuditRecord record, String status) {
        return new TokenAuditResponse(
            record.tokenId(),
            record.source(),
            record.token(),
            record.tokenSha256(),
            record.username(),
            record.deviceId(),
            record.deviceName(),
            record.clientId(),
            record.authorizationId(),
            record.issuedAt(),
            record.expiresAt(),
            record.revokedAt(),
            status
        );
    }
}
