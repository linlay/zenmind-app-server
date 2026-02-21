package com.agw.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UserPasswordResetRequest(@NotBlank String password) {
}
