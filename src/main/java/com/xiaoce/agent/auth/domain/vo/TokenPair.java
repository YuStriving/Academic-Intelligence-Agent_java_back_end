package com.xiaoce.agent.auth.domain.vo;

import java.time.Instant;

public record TokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String refreshTokenId
) {
}
