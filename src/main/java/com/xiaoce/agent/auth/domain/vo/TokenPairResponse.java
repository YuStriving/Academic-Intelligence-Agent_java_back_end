package com.xiaoce.agent.auth.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 访问令牌与刷新令牌的组合。
 * <p>
 * 用于封装登录成功或刷新 Token 后返回给前端的完整令牌信息。
 * 以便前端检测访问令牌过期调用获取新的访问令牌接口
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPairResponse {
    private String accessToken;
    private String refreshToken;
    private Instant expiresIn;
    private Instant refreshExpiresIn;
}
