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

import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.USER_IS_BANNED_BITMAP_KEY;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.USER_TOKEN_VERSION_KEY_PREFIX;

/**
 * JWT认证转换器（请求身份验证网关）
 * 
 * <p>这个类是Spring Security认证流程的核心组件。每当用户访问受保护接口时，
 * Spring Security会自动调用此转换器，将HTTP请求头中的JWT令牌转换为
 * 系统内部的认证对象（JwtAuthenticationToken）。
 * 
 * <p>在Spring Security工作流程中的位置：
 * <pre>
 * 1. 用户发送HTTP请求，携带Authorization: Bearer {accessToken}
 * 2. Spring Security的OAuth2ResourceServer过滤器拦截请求
 * 3. 解析JWT令牌（验证签名、过期时间等）
 * 4. 调用此转换器进行业务验证（令牌类型、版本号、封禁状态）
 * 5. 转换为JwtAuthenticationToken对象
 * 6. 存入SecurityContext，后续业务代码可通过SecurityContextHolder获取
 * </pre>
 * 
 * <p>验证内容（三重安全检查）：
 * <pre>
 * 检查1：令牌类型验证
 * - 确保令牌是access token（不能用refresh token访问接口）
 * - 防止令牌混用导致的安全问题
 * 
 * 检查2：令牌版本验证
 * - 对比JWT中的版本号与Redis中的当前版本号
 * - 如果版本号不匹配，说明用户已强制登出（如修改密码、全设备登出）
 * - 这是实现"全设备强制登出"功能的关键检查点
 * 
 * 检查3：用户封禁状态验证
 * - 检查Redis中的封禁标记（使用Bitmap高效存储）
 * - 如果用户被封禁，立即拒绝访问
 * - 管理员封禁用户后，被封禁用户的所有令牌立即失效
 * </pre>
 * 
 * <p>与其他组件的配合：
 * <ul>
 *   <li>SecurityConfig：配置OAuth2资源服务器时注册此转换器</li>
 *   <li>JwtTokenService：提供令牌解析和版本验证功能</li>
 *   <li>RedisTemplate：查询令牌版本号和用户封禁状态</li>
 *   <li>JwtAuthenticationToken：转换后返回的认证对象</li>
 *   <li>UsersMapper：（预留）可用于查询用户详细信息</li>
 * </ul>
 * 
 * <p>异常处理：
 * <ul>
 *   <li>BadCredentialsException：令牌无效时抛出，被Spring Security捕获后返回401</li>
 *   <li>RestAuthenticationEntryPoint：统一处理认证失败，返回标准JSON错误响应</li>
 * </ul>
 * 
 * <p>注意事项：
 * <ul>
 *   <li>此转换器只验证accessToken，不处理refreshToken</li>
 *   <li>每次请求都会执行验证，因此Redis查询必须高效</li>
 *   <li>如果Redis不可用，会导致所有请求失败（需要做好Redis高可用）</li>
 * </ul>
 * 
 * @author xiaoce
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    // ==================== 依赖注入 ====================
    
    /**
     * JWT令牌服务（用于解析和验证令牌）
     */
    private final JwtTokenService jwtTokenService;
    
    /**
     * Redis操作模板（用于查询令牌版本号和封禁状态）
     */
    private final RedisTemplate<String, String> redisTemplate;

    // ==================== 核心方法 ====================

    /**
     * 转换JWT令牌为Spring Security认证对象（核心转换逻辑）
     * 
     * <p>这是整个认证流程最关键的方法。当用户访问受保护接口时，
     * Spring Security调用此方法将JWT令牌转换为系统内部的认证对象。
     * 
     * <p>转换流程：
     * <pre>
     * 输入：已解析的JWT对象（由Spring Security的JwtDecoder解析）
     * 
     * 步骤1：验证令牌类型
     * - 从JWT的claims中提取token_type字段
     * - 必须是"access"（访问令牌）
     * - 如果是"refresh"（刷新令牌），拒绝访问
     * - 防止用refreshToken访问需要accessToken的接口
     * 
     * 步骤2：解析用户ID
     * - 从JWT的subject字段提取用户ID
     * - 验证用户ID格式是否合法（必须是正整数）
     * 
     * 步骤3：验证令牌版本（关键安全检查）
     * - 从Redis获取用户的当前令牌版本号
     * - 对比JWT中的版本号与Redis中的版本号
     * - 如果不匹配，说明用户已执行强制登出，令牌失效
     * - 这是实现"全设备强制登出"的核心机制
     * 
     * 步骤4：检查用户封禁状态
     * - 从Redis的Bitmap中查询用户是否被封禁
     * - 如果被封禁，立即拒绝访问
     * - 管理员封禁用户后，所有令牌立即失效
     * 
     * 步骤5：提取用户昵称
     * - 从JWT的claims中提取nickName字段
     * - 用于后续业务逻辑（如日志记录、权限显示）
     * 
     * 步骤6：构建认证对象
     * - 创建JwtUserInfo对象（包含userId和nickname）
     * - 创建JwtAuthenticationToken对象（包含用户信息和原始JWT）
     * - 返回认证对象，存入SecurityContext
     * 
     * 输出：JwtAuthenticationToken对象（包含用户身份信息）
     * </pre>
     * 
     * <p>使用场景示例：
     * <pre>
     * 场景1：用户访问受保护接口
     * GET /api/v1/users/profile
     * Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
     * 
     * Spring Security流程：
     * 1. OAuth2ResourceServer过滤器拦截请求
     * 2. 提取Authorization头中的JWT令牌
     * 3. JwtDecoder验证签名和过期时间
     * 4. 调用此convert()方法进行业务验证
     * 5. 验证通过后，创建JwtAuthenticationToken
     * 6. 存入SecurityContext，业务代码可获取当前用户信息
     * 
     * 场景2：令牌已失效（用户修改密码后）
     * 1. 用户在设备A修改密码
     * 2. 系统将Redis中的令牌版本号递增（0 -> 1）
     * 3. 用户在设备B使用旧令牌访问接口
     * 4. 此转换器验证时发现版本号不匹配（0 != 1）
     * 5. 抛出BadCredentialsException
     * 6. RestAuthenticationEntryPoint返回401错误
     * 7. 设备B需要重新登录
     * </pre>
     * 
     * <p>异常处理：
     * <ul>
     *   <li>BadCredentialsException：令牌类型错误、版本不匹配、用户被封禁等</li>
     *   <li>其他Exception：统一转换为BadCredentialsException，避免暴露内部细节</li>
     *   <li>所有异常都会被RestAuthenticationEntryPoint捕获，返回标准401响应</li>
     * </ul>
     * 
     * @param jwt 已解析的JWT令牌对象（由Spring Security的JwtDecoder解析）
     * @return JwtAuthenticationToken对象（包含用户身份信息和原始JWT）
     * @throws BadCredentialsException 当令牌无效、版本不匹配或用户被封禁时抛出
     */
    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        try {
            // ==================== 安全检查1：验证令牌类型 ====================
            // 确保令牌是access token，不能用refresh token访问接口
            // 这是第一道防线，防止令牌混用
            if (!JwtTokenService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE))) {
                log.debug("JWT令牌类型不匹配 - 期望: {}, 实际: {}", 
                        JwtTokenService.TYPE_ACCESS, jwt.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE));
                throw new BadCredentialsException("Invalid access token");
            }

            // ==================== 安全检查2：解析用户ID ====================
            // 从JWT的subject字段提取用户ID
            // subject是JWT标准字段，存储的是用户ID的字符串形式
            // parseSubjectAsUserId会验证格式（必须是正整数）
            Long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
            
            // ==================== 安全检查3：验证令牌版本 ====================
            // 这是实现"全设备强制登出"的核心机制
            // 1. 从Redis获取用户的当前令牌版本号
            // 2. 对比JWT中的版本号与Redis中的版本号
            // 3. 如果不匹配，说明用户已执行强制登出（如修改密码、全设备登出）
            long currentVersion = getCurrentTokenVersion(userId);
            jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_ACCESS);
            
            // ==================== 安全检查4：检查用户封禁状态 ====================
            // 使用Redis Bitmap高效存储和查询封禁状态
            // Bitmap的优势：占用空间极小（1亿用户只需12.5MB）
            // 如果用户被封禁，立即拒绝访问，所有令牌失效
            Boolean banned = redisTemplate.opsForValue().getBit(USER_IS_BANNED_BITMAP_KEY, userId);
            if (Boolean.TRUE.equals(banned)) {
                throw new BadCredentialsException("User is banned");
            }
            
            // ==================== 步骤5：提取用户昵称 ====================
            // 从JWT的claims中提取nickName字段
            // 用于后续业务逻辑（如日志记录、权限显示）
            String nickname = jwt.getClaimAsString("nickName");
            
            // ==================== 步骤6：构建认证对象 ====================
            // 创建用户信息对象（包含userId和nickname）
            JwtUserInfo userInfo = new JwtUserInfo(userId, nickname);
            
            // 记录调试日志（生产环境通常不打印，避免敏感信息泄露）
            log.debug("JWT认证转换成功 - 用户ID: {}, 昵称: {}", userId, nickname);
            
            // 创建并返回认证对象
            // 包含用户信息和原始JWT令牌（用于后续业务逻辑）
            return new JwtAuthenticationToken(userInfo, jwt.getTokenValue());
        } catch (BadCredentialsException e) {
            // 业务异常直接抛出，保持原有错误信息
            throw e;
        } catch (Exception e) {
            // 其他异常统一转换为BadCredentialsException
            // 这样可以避免暴露内部技术细节给调用方
            log.debug("JWT认证转换失败 - 错误: {}", e.getMessage());
            throw new BadCredentialsException("Invalid access token", e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取用户当前令牌版本号（版本查询器）
     * 
     * <p>从Redis中读取用户的当前令牌版本号。
     * 这个版本号是"全设备强制登出"功能的核心数据。
     * 
     * <p>工作原理：
     * <pre>
     * Redis存储格式：
     * Key: user:token:version:{userId}
     * Value: {versionNumber}（字符串形式的数字）
     * 例如：user:token:version:1001 -> "3"
     * 
     * 版本号递增场景：
     * 1. 用户修改密码 -> 版本号+1
     * 2. 用户全设备登出 -> 版本号+1
     * 3. 管理员封禁用户 -> 版本号+1（可选）
     * 
     * 验证流程：
     * 1. 用户登录时，将当前版本号写入JWT
     * 2. 用户访问接口时，从JWT提取版本号
     * 3. 从Redis读取当前版本号
     * 4. 对比两个版本号：
     *    - 匹配：令牌有效
     *    - 不匹配：令牌已失效，需要重新登录
     * </pre>
     * 
     * <p>容错处理：
     * <ul>
     *   <li>如果Redis中不存在版本号，返回0（初始版本）</li>
     *   <li>如果版本号格式错误（不是数字），返回0（容错处理）</li>
     *   <li>这样设计是为了保证历史令牌的向后兼容性</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @return 当前令牌版本号（如果不存在则返回0）
     */
    private long getCurrentTokenVersion(Long userId) {
        // 从Redis读取用户的当前令牌版本号
        String raw = redisTemplate.opsForValue().get(USER_TOKEN_VERSION_KEY_PREFIX + userId);
        
        // 如果Redis中不存在版本号，返回0（初始版本）
        // 这表示用户从未执行过强制登出操作
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            // 将字符串解析为数字
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            // 版本号格式错误，返回0（容错处理）
            // 这可能是Redis数据损坏或恶意篡改
            // 返回0可以保证历史令牌仍然有效
            return 0L;
        }
    }
}
