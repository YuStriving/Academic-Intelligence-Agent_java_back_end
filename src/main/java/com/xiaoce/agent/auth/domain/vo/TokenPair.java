package com.xiaoce.agent.auth.domain.vo;

import java.time.Instant;

/**
 * TokenPair
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/22 14:44
 */public record TokenPair(
        String accessToken,
        /** 访问令牌过期时间 Instant 是 Java 8 引入的 “时间线上的一个瞬时点”。*/
        Instant accessTokenExpiresAt,

        /** 刷新令牌 (JWT 字符串，仅用于刷新接口) */
        String refreshToken,

        /** 刷新令牌过期时间 */
        Instant refreshTokenExpiresAt,

        /** 刷新令牌 ID (jti，用于白名单存储与撤销) */
        String refreshTokenId
) {
}
