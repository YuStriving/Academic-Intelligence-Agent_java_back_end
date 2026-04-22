package com.xiaoce.agent.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PasswordResetRequest
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/22 12:42
 */
public record PasswordResetRequest(
        @NotBlank(message =  "邮箱或者用户名不能为空")
        String emailOrUsername,
        @NotBlank(message = "密码不能为空")
        String password,
        @NotBlank(message = "新密码不能为空")
        String newPassword,
        @NotBlank(message = "验证码不能为空")
        String code
) {
}
