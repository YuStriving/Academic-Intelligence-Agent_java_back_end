package com.xiaoce.agent.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank(message = "Email or username is required")
        String emailOrUsername,
        @NotBlank(message = "Password is required")
        String password,
        @NotBlank(message = "New password is required")
        String newPassword,
        @NotBlank(message = "Verification code is required")
        String code
) {
}
