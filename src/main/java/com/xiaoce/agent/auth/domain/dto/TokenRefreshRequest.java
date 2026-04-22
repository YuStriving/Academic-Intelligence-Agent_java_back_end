package com.xiaoce.agent.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * TokenRefreshRequest
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/22 12:07
 */
public record TokenRefreshRequest(
        @NotBlank(message = "访问令牌不能为空")
        String refreshToken
) {
}
