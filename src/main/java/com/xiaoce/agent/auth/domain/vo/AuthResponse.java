package com.xiaoce.agent.auth.domain.vo;

/**
 * AuthResponse
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/22 11:31
 */
public record AuthResponse(

        TokenPairResponse token,
        UserInfoResponse user
) {
}
