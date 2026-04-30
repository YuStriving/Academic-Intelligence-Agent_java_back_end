package com.xiaoce.agent.auth.domain.vo;

import java.time.Instant;

/**
 * AuthNoRefreshTokenResponse
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/27 15:15
 */
public record AuthNoRefreshTokenResponse(
        String accessToken,
        Instant expiresIn,
        UserInfoResponse userInfoResponse

) {
    public  static  AuthNoRefreshTokenResponse toAuthNoRefreshTokenResponse(AuthResponse authResponse){
        return new AuthNoRefreshTokenResponse(authResponse.token().accessToken(),
                authResponse.token().expiresIn(),
                authResponse.user()
        );
    }
}
