package com.xiaoce.agent.auth.domain.vo;

import java.time.Instant;

/**
 * AuthAccessToken
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/27 15:23
 */
public record AuthAccessToken(
        String accessToken,
        Instant expireIn
) {
    public static AuthAccessToken toAuthAccessToken(TokenPairResponse tokenPair){
        return new AuthAccessToken(tokenPair.accessToken(), tokenPair.expiresIn());
    }

}
