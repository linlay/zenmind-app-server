package com.app.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.app.auth.config.AuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AppInternalEventAuthService {

    private static final long MAX_SKEW_SECONDS = 300;

    private final AuthProperties authProperties;

    public AppInternalEventAuthService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public void verifyOrThrow(String timestampHeader, String signatureHeader, String requestBody) {
        if (!StringUtils.hasText(timestampHeader) || !StringUtils.hasText(signatureHeader)) {
            throw new IllegalArgumentException("missing internal signature headers");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid internal timestamp");
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > MAX_SKEW_SECONDS) {
            throw new IllegalArgumentException("expired internal request");
        }

        String expected = sign(timestampHeader.trim(), requestBody == null ? "" : requestBody);
        if (!MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signatureHeader.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("invalid internal signature");
        }
    }

    public String sign(String timestamp, String body) {
        String secret = authProperties.getApp().getInternalWebhookSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("auth.app.internal-webhook-secret must be configured");
        }

        String message = timestamp + "." + (body == null ? "" : body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign internal request", ex);
        }
    }
}

