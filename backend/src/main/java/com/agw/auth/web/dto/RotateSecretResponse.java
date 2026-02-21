package com.agw.auth.web.dto;

public record RotateSecretResponse(String clientId, String newClientSecret) {
}
