package com.app.auth.service;

import java.time.Duration;

import com.app.auth.config.AuthProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SecurityDataCleanupScheduler {

    private final AuthProperties authProperties;
    private final DeviceService deviceService;
    private final TokenAuditService tokenAuditService;

    public SecurityDataCleanupScheduler(
        AuthProperties authProperties,
        DeviceService deviceService,
        TokenAuditService tokenAuditService
    ) {
        this.authProperties = authProperties;
        this.deviceService = deviceService;
        this.tokenAuditService = tokenAuditService;
    }

    @Scheduled(cron = "${auth.cleanup.cron:0 0 * * * *}")
    public void cleanupExpiredSecurityData() {
        Duration retention = authProperties.getCleanup().getRetention();
        deviceService.deleteRevokedOlderThan(retention);
        tokenAuditService.deleteIssuedOlderThan(retention);
    }
}
