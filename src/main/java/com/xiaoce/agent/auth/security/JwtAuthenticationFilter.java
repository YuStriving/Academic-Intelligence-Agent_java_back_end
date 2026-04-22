package com.xiaoce.agent.auth.security;

import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import com.xiaoce.agent.auth.service.impl.JwtTokenService;
import com.xiaoce.agent.auth.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器 - 身份证检查站
 * 
 * 作用：在每个HTTP请求到达控制器之前，检查请求头中的JWT token是否有效
 * 
 * 工作流程：
 * 1. 检查请求头中是否有 Authorization: Bearer token
 * 2. 提取并验证token的有效性
 * 3. 如果token有效，创建认证对象并放入安全上下文
 * 4. 如果token无效，清除安全上下文
 * 5. 继续执行后续过滤器链
 * 
 * 这个过滤器是Spring Security过滤器链的一部分，在UsernamePasswordAuthenticationFilter之前执行
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Bearer token前缀，标准格式：Authorization: Bearer eyJ...
    private static final String BEARER_PREFIX = "Bearer ";

    // 依赖注入：JWT令牌服务，用于解析和验证token
    private final JwtTokenService jwtTokenService;

    /**
     * 核心方法：处理每个HTTP请求的JWT认证
     * 
     * 执行逻辑：
     * 1. 检查请求头中是否有Authorization头，并且以"Bearer "开头
     * 2. 检查当前安全上下文中是否已经有认证信息（避免重复认证）
     * 3. 提取token并进行验证
     * 4. 验证成功则创建认证对象，失败则清除安全上下文
     * 5. 无论认证结果如何，都继续执行后续过滤器链
     * 
     * 为什么需要这个过滤器？
     * - 传统Web应用使用session来保持用户状态
     * - 现代API应用使用无状态设计，每次请求都携带token
     * - 这个过滤器就是用来验证token，替代session的作用
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // 1. 获取Authorization请求头
        String header = request.getHeader("Authorization");
        
        // 2. 检查是否有Bearer token，并且当前没有认证信息（避免重复认证）
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            
            // 3. 提取token（去掉"Bearer "前缀）
            String token = header.substring(BEARER_PREFIX.length()).trim();
            
            // 4. 确保token不为空
            if (!token.isEmpty()) {
                try {
                    Jwt jwt = jwtTokenService.decodeJwt(token);
                    // 5. 使用JWT服务解析和验证访问令牌
                    JwtUserInfo jwtUserInfo = jwtTokenService.extractUserInfo(jwt);
                    
                    // 6. 创建JWT认证令牌对象，直接使用从JWT中提取的用户信息
                    JwtAuthenticationToken authenticationToken =
                            new JwtAuthenticationToken(jwtUserInfo, token);
                    
                    // 7. 设置认证详细信息（请求来源、IP等）
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    // 8. 将认证对象放入安全上下文，后续代码可以通过@AuthenticationPrincipal获取
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    
                } catch (BusinessException ignored) {
                    // 9. 业务异常（如token过期、无效等），清除安全上下文
                    SecurityContextHolder.clearContext();
                } catch (Exception ignored) {
                    // 10. 其他异常（如解析错误），清除安全上下文
                    SecurityContextHolder.clearContext();
                }
            }
        }
        // 12. 继续执行后续过滤器链（无论认证成功与否）
        filterChain.doFilter(request, response);
    }
}
