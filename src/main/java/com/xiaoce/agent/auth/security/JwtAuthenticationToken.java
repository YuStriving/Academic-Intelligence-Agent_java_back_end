package com.xiaoce.agent.auth.security;

import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * 自定义JWT认证令牌类 - 扩展Spring Security OAuth2标准JwtAuthenticationToken
 * 
 * 作用：在标准JwtAuthenticationToken基础上，添加对JwtUserInfo的支持
 * 实现：继承标准JwtAuthenticationToken，提供额外的用户信息访问方法
 * 
 * 设计优势：
 * - 兼容Spring Security OAuth2标准架构
 * - 提供类型安全的用户信息访问
 * - 支持@AuthenticationPrincipal注解
 * - 保持与现有代码的兼容性
 */
public class JwtAuthenticationToken extends JwtAuthenticationToken {

    private final JwtUserInfo userInfo;

    /**
     * 构造函数 - 创建已认证的JWT令牌对象
     *
     * @param jwt Spring Security OAuth2的JWT对象
     * @param authorities 权限集合
     * @param userInfo JWT用户信息对象
     */
    public JwtAuthenticationToken(Jwt jwt, Collection<? extends GrantedAuthority> authorities, JwtUserInfo userInfo) {
        super(jwt, authorities);
        this.userInfo = userInfo;
    }

    /**
     * 获取用户ID
     */
    public long getUserId() {
        return userInfo != null ? userInfo.userId() : 0L;
    }

    /**
     * 获取JWT用户信息对象
     */
    public JwtUserInfo getUserInfo() {
        return userInfo;
    }

    /**
     * 获取用户名
     */
    public String getUsername() {
        return userInfo != null ? userInfo.nickname() : "anonymous";
    }

    /**
     * 检查用户是否具有指定角色
     */
    public boolean hasRole(String role) {
        return userInfo != null && userInfo.roles() != null && userInfo.roles().contains(role);
    }

    /**
     * 检查用户是否是管理员
     */
    public boolean isAdmin() {
        return hasRole("ROLE_ADMIN") || hasRole("ROLE_SUPER_ADMIN");
    }

    /**
     * 检查用户是否是超级管理员
     */
    public boolean isSuperAdmin() {
        return hasRole("ROLE_SUPER_ADMIN");
    }

    @Override
    public Object getPrincipal() {
        // 返回用户信息对象，支持@AuthenticationPrincipal注解
        return userInfo != null ? userInfo : super.getPrincipal();
    }

    @Override
    public String toString() {
        return "JwtAuthenticationToken{" +
                "userId=" + getUserId() +
                ", username='" + getUsername() + '\'' +
                ", authorities=" + getAuthorities() +
                '}';
    }
}