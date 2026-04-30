package com.xiaoce.agent.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * ForgetPassword
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/25 15:07
 */
public record ForgetPasswordRequest(
        @NotBlank
        String emailOrUsername,
        @NotBlank
        String code,
        @NotBlank
        String newPassword

) {
}
