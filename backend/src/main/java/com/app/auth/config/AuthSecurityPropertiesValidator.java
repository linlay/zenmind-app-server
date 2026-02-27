package com.app.auth.config;

import jakarta.annotation.PostConstruct;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthSecurityPropertiesValidator {

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final AuthProperties authProperties;

    public AuthSecurityPropertiesValidator(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @PostConstruct
    void validate() {
        String adminUsername = normalizeQuotedValue(authProperties.getAdmin().getUsername());
        String appUsername = normalizeQuotedValue(authProperties.getApp().getUsername());
        String adminHash = normalizeQuotedValue(authProperties.getAdmin().getPasswordBcrypt());
        String appMasterHash = normalizeQuotedValue(authProperties.getApp().getMasterPasswordBcrypt());
        authProperties.getAdmin().setUsername(adminUsername);
        authProperties.getApp().setUsername(appUsername);
        authProperties.getAdmin().setPasswordBcrypt(adminHash);
        authProperties.getApp().setMasterPasswordBcrypt(appMasterHash);

        requireBcrypt("AUTH_ADMIN_PASSWORD_BCRYPT", adminHash);
        requireBcrypt("AUTH_APP_MASTER_PASSWORD_BCRYPT", appMasterHash);
    }

    private static void requireBcrypt(String key, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(key + " is required and must be a bcrypt hash");
        }
        if (isUnresolvedPlaceholder(value)) {
            throw new IllegalStateException(key + " is not configured. Please set it in .env");
        }
        if (!BCRYPT_PATTERN.matcher(value).matches()) {
            throw new IllegalStateException(key + " must be a valid bcrypt hash");
        }
    }

    private static boolean isUnresolvedPlaceholder(String value) {
        return value.contains("${") && value.contains("}");
    }

    private static String normalizeQuotedValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }
}
