package com.xiaoce.agent.auth.security;

import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.List;

/**
 * JWT 认证令牌类 - Spring Security 认证对象
 * 核心职责：在 SecurityContext 中持有不可变的 JwtUserInfo 业务对象
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final JwtUserInfo userInfo;
    private final String rawToken;

    /**
     * 构造函数 - 创建已认证的 JWT 令牌对象
     *
     * @param userInfo JWT 用户信息对象 (Record)
     * @param rawToken 原始 JWT 令牌
     */
    public JwtAuthenticationToken(JwtUserInfo userInfo, String rawToken) {
        super(extractAuthorities(userInfo));

        Assert.notNull(userInfo, "JwtUserInfo cannot be null");
        Assert.hasText(rawToken, "rawToken cannot be empty");

        this.userInfo = userInfo;
        this.rawToken = rawToken;

        super.setAuthenticated(true);
    }

    private static Collection<? extends GrantedAuthority> extractAuthorities(JwtUserInfo userInfo) {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public long getUserId() {
        return userInfo.userId();
    }

    public JwtUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public Object getCredentials() {
        return rawToken;
    }

    @Override
    public Object getPrincipal() {
        return userInfo;
    }
}
