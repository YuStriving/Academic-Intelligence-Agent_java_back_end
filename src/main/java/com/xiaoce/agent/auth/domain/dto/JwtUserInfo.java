package com.xiaoce.agent.auth.domain.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * JwtUserInfo
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/22 16:03
 */
public record JwtUserInfo(
        @NotNull(message = "User id is required")
         Long userId,
         String nickname
) {
}
