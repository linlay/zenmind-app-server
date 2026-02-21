package com.agw.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private String issuer;
    private final Token token = new Token();
    private final Admin admin = new Admin();
    private final App app = new App();
    private final BootstrapUser bootstrapUser = new BootstrapUser();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Token getToken() {
        return token;
    }

    public Admin getAdmin() {
        return admin;
    }

    public App getApp() {
        return app;
    }

    public BootstrapUser getBootstrapUser() {
        return bootstrapUser;
    }

    public static class Token {
        private Duration accessTtl = Duration.ofMinutes(15);
        private Duration refreshTtl = Duration.ofDays(30);
        private boolean rotateRefreshToken = true;

        public Duration getAccessTtl() {
            return accessTtl;
        }

        public void setAccessTtl(Duration accessTtl) {
            this.accessTtl = accessTtl;
        }

        public Duration getRefreshTtl() {
            return refreshTtl;
        }

        public void setRefreshTtl(Duration refreshTtl) {
            this.refreshTtl = refreshTtl;
        }

        public boolean isRotateRefreshToken() {
            return rotateRefreshToken;
        }

        public void setRotateRefreshToken(boolean rotateRefreshToken) {
            this.rotateRefreshToken = rotateRefreshToken;
        }
    }

    public static class Admin {
        private String username;
        private String passwordBcrypt;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordBcrypt() {
            return passwordBcrypt;
        }

        public void setPasswordBcrypt(String passwordBcrypt) {
            this.passwordBcrypt = passwordBcrypt;
        }
    }

    public static class App {
        private String username = "app";
        private String masterPasswordBcrypt;
        private Duration accessTtl = Duration.ofMinutes(10);
        private Duration maxAccessTtl = Duration.ofHours(12);
        private boolean rotateDeviceToken = true;
        private String internalWebhookSecret = "change-me";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getMasterPasswordBcrypt() {
            return masterPasswordBcrypt;
        }

        public void setMasterPasswordBcrypt(String masterPasswordBcrypt) {
            this.masterPasswordBcrypt = masterPasswordBcrypt;
        }

        public Duration getAccessTtl() {
            return accessTtl;
        }

        public void setAccessTtl(Duration accessTtl) {
            this.accessTtl = accessTtl;
        }

        public Duration getMaxAccessTtl() {
            return maxAccessTtl;
        }

        public void setMaxAccessTtl(Duration maxAccessTtl) {
            this.maxAccessTtl = maxAccessTtl;
        }

        public boolean isRotateDeviceToken() {
            return rotateDeviceToken;
        }

        public void setRotateDeviceToken(boolean rotateDeviceToken) {
            this.rotateDeviceToken = rotateDeviceToken;
        }

        public String getInternalWebhookSecret() {
            return internalWebhookSecret;
        }

        public void setInternalWebhookSecret(String internalWebhookSecret) {
            this.internalWebhookSecret = internalWebhookSecret;
        }
    }

    public static class BootstrapUser {
        private String username;
        private String passwordBcrypt;
        private String displayName;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordBcrypt() {
            return passwordBcrypt;
        }

        public void setPasswordBcrypt(String passwordBcrypt) {
            this.passwordBcrypt = passwordBcrypt;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}
