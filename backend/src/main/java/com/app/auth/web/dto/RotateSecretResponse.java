package com.app.auth.web.dto;

public record RotateSecretResponse(String clientId, String newClientSecret) {
}
