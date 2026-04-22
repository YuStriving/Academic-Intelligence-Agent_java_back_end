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
 * * 核心职责：在 SecurityContext 中持有不可变的 JwtUserInfo 业务对象
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
        // 1. 抽取权限解析逻辑，使 super 调用更清晰
        super(extractAuthorities(userInfo));

        // 2. 引入参数校验 (Fail-Fast 机制)
        Assert.notNull(userInfo, "JwtUserInfo cannot be null");
        Assert.hasText(rawToken, "rawToken cannot be empty");

        this.userInfo = userInfo;
        this.rawToken = rawToken;

        // 3. 到达此处说明已通过 JWT 校验，直接放行
        super.setAuthenticated(true);
    }

    /**
     * 解析用户权限：如果有 roles 则映射，否则给予默认角色
     */
    private static Collection<? extends GrantedAuthority> extractAuthorities(JwtUserInfo userInfo) {
        if (userInfo == null || userInfo.roles() == null || userInfo.roles().isEmpty()) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return userInfo.roles().stream()
                .map(SimpleGrantedAuthority::new)
                .toList(); // 使用 Java 16+ 的 toList()，更简洁
    }

    public long getUserId() {
        // 构造函数已确保 userInfo 不为空，直接调用即可
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
        // 直接返回 userInfo 对象，方便 Controller 中 @AuthenticationPrincipal 强转获取
        return userInfo;
    }
}