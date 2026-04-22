package com.xiaoce.agent.auth.domain.dto;

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
         Long userId,
         String nickname,
         List<String> roles
) {
}
