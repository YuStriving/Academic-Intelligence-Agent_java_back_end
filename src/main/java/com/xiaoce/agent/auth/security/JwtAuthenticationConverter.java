package com.xiaoce.agent.auth.security;

import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import com.xiaoce.agent.auth.service.impl.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.xiaoce.agent.auth.common.constant.RefreshToken.USER_TOKEN_VERSION_KEY_PREFIX;

/**
 * JWT认证转换器
 * 
 * <p>将解析后的JWT令牌转换为Spring Security的认证对象，同时验证令牌的有效性。
 * 这是OAuth2资源服务器认证流程中的关键组件。
 * 
 * <p>验证内容：
 * <ul>
 *   <li>验证令牌类型是否为访问令牌（不是刷新令牌）</li>
 *   <li>验证令牌版本是否有效（防止被全设备登出的令牌）</li>
 *   <li>提取用户信息构建认证对象</li>
 * </ul>
 * 
 * <p>使用场景：
 * <ul>
 *   <li>用户访问受保护接口时，Spring Security自动调用此转换器</li>
 *   <li>验证JWT令牌并构建认证上下文</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private final JwtTokenService jwtTokenService;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 转换JWT令牌为认证对象
     * 
     * <p>执行以下验证步骤：
     * <ol>
     *   <li>验证令牌类型是否为访问令牌</li>
     *   <li>解析用户ID</li>
     *   <li>验证令牌版本是否有效</li>
     *   <li>提取用户昵称</li>
     *   <li>构建并返回认证对象</li>
     * </ol>
     * 
     * @param jwt 解析后的JWT令牌对象
     * @return Spring Security认证对象
     * @throws BadCredentialsException 当令牌无效时抛出
     */
    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        try {
            // 1. 验证令牌类型是否为访问令牌（不能用刷新令牌访问接口）
            if (!JwtTokenService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE))) {
                log.debug("JWT令牌类型不匹配 - 期望: {}, 实际: {}", 
                        JwtTokenService.TYPE_ACCESS, jwt.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE));
                throw new BadCredentialsException("Invalid access token");
            }

            // 2. 解析用户ID
            Long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
            
            // 3. 获取当前令牌版本并验证
            long currentVersion = getCurrentTokenVersion(userId);
            jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_ACCESS);

            // 4. 提取用户昵称
            String nickname = jwt.getClaimAsString("nickName");
            
            // 5. 构建用户信息和认证对象
            JwtUserInfo userInfo = new JwtUserInfo(userId, nickname);
            
            log.debug("JWT认证转换成功 - 用户ID: {}, 昵称: {}", userId, nickname);
            
            return new JwtAuthenticationToken(userInfo, jwt.getTokenValue());
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.debug("JWT认证转换失败 - 错误: {}", e.getMessage());
            throw new BadCredentialsException("Invalid access token", e);
        }
    }

    /**
     * 获取用户当前令牌版本
     * 
     * <p>从Redis中读取用户的令牌版本号，如果不存在则返回0。
     * 令牌版本号用于实现全设备登出功能。
     * 
     * @param userId 用户ID
     * @return 当前令牌版本号
     */
    private long getCurrentTokenVersion(Long userId) {
        String raw = redisTemplate.opsForValue().get(USER_TOKEN_VERSION_KEY_PREFIX + userId);
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
