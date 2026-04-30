package com.xiaoce.agent.auth.security;

import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.List;

/**
 * JWT认证令牌类（Spring Security认证对象容器）
 * 
 * <p>这个类是Spring Security框架中的"认证令牌"，用于在安全上下文中
 * 存储已认证用户的身份信息。你可以把它理解为"通过安检后的通行证"。
 * 
 * <p>在Spring Security工作流程中的作用：
 * <pre>
 * 1. 用户请求 -> 2. JWT验证 -> 3. 创建此对象 -> 4. 存入SecurityContext
 * 
 * 后续业务代码获取用户信息：
 * JwtAuthenticationToken authToken = (JwtAuthenticationToken) 
 *     SecurityContextHolder.getContext().getAuthentication();
 * Long userId = authToken.getUserId(); // 获取当前用户ID
 * JwtUserInfo userInfo = authToken.getUserInfo(); // 获取完整用户信息
 * </pre>
 * 
 * <p>核心职责：
 * <ul>
 *   <li>持有不可变的用户身份信息（JwtUserInfo）</li>
 *   <li>持有原始JWT令牌字符串（用于审计日志等场景）</li>
 *   <li>提供用户权限信息（默认为ROLE_USER）</li>
 *   <li>作为Spring Security认证状态的载体</li>
 * </ul>
 * 
 * <p>与其他组件的配合：
 * <ul>
 *   <li>JwtAuthenticationConverter：创建此对象并返回</li>
 *   <li>SecurityContext：存储此对象，后续请求可获取当前用户</li>
 *   <li>业务Controller/Service：通过SecurityContextHolder获取此对象</li>
 * </ul>
 * 
 * <p>为什么继承AbstractAuthenticationToken？
 * Spring Security要求所有认证对象必须实现Authentication接口。
 * AbstractAuthenticationToken是Spring提供的抽象基类，简化了实现。
 * 继承后需要实现：
 * - getCredentials()：返回凭证（这里返回原始JWT）
 * - getPrincipal()：返回主体（这里返回用户信息）
 * - getAuthorities()：返回权限列表（这里返回ROLE_USER）
 * 
 * @author xiaoce
 * @since 1.0
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    // ==================== 字段定义 ====================
    
    /**
     * 用户身份信息（不可变）
     * 包含用户ID和显示名称，从JWT中解析而来
     */
    private final JwtUserInfo userInfo;
    
    /**
     * 原始JWT令牌字符串
     * 用于审计日志、令牌追踪等场景
     */
    private final String rawToken;

    // ==================== 构造方法 ====================

    /**
     * 构造函数 - 创建已认证的JWT令牌对象
     * 
     * <p>当JWT验证通过后，由JwtAuthenticationConverter调用此构造函数
     * 创建认证对象。构造函数会自动设置认证状态为true（已认证）。
     * 
     * <p>构造流程：
     * <pre>
     * 1. 调用父类构造函数，传入权限列表（ROLE_USER）
     * 2. 验证参数不为空（防御性编程）
     * 3. 赋值用户信息和原始令牌
     * 4. 设置认证状态为true（表示已通过验证）
     * </pre>
     * 
     * @param userInfo JWT用户信息对象，包含用户ID和显示名称
     * @param rawToken 原始JWT令牌字符串（用于审计和追踪）
     * @throws IllegalArgumentException 如果userInfo或rawToken为空
     */
    public JwtAuthenticationToken(JwtUserInfo userInfo, String rawToken) {
        // 调用父类构造函数，传入用户权限列表
        // Spring Security会根据权限判断用户是否有权限访问某些接口
        super(extractAuthorities(userInfo));

        // 参数验证（防御性编程）
        // 确保必要参数不为空，防止后续使用出现空指针异常
        Assert.notNull(userInfo, "JwtUserInfo cannot be null");
        Assert.hasText(rawToken, "rawToken cannot be empty");

        // 赋值字段
        this.userInfo = userInfo;
        this.rawToken = rawToken;

        // 设置认证状态为true
        // 这表示用户已经通过验证，是已认证状态
        // Spring Security会根据此状态决定是否允许访问受保护接口
        super.setAuthenticated(true);
    }

    // ==================== 私有方法 ====================

    /**
     * 提取用户权限列表
     * 
     * <p>根据用户信息构建权限列表，用于Spring Security的权限控制。
     * 当前系统采用简化设计，所有用户都赋予ROLE_USER角色。
     * 
     * <p>权限设计说明：
     * <pre>
     * 当前设计：
     * - 所有用户都是ROLE_USER（普通用户）
     * - 没有区分管理员、VIP等特殊角色
     * 
     * 未来扩展：
     * 如果需要多角色支持，可以：
     * 1. 在JwtUserInfo中添加role字段
     * 2. 根据role动态生成权限列表
     * 例如：
     * if (userInfo.isAdmin()) {
     *     authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
     * }
     * </pre>
     * 
     * @param userInfo 用户信息对象
     * @return 权限列表（当前固定返回ROLE_USER）
     */
    private static Collection<? extends GrantedAuthority> extractAuthorities(JwtUserInfo userInfo) {
        // 所有用户默认赋予ROLE_USER角色
        // Spring Security使用ROLE_前缀标识角色
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // ==================== 公开方法 ====================

    /**
     * 获取用户ID（快捷方法）
     * 
     * <p>这是最常用的方法，业务代码需要知道"当前谁在操作"时会调用此方法。
     * 
     * <p>使用示例：
     * <pre>
     * // Controller中获取当前用户ID
     * @GetMapping("/profile")
     * public ResponseEntity<?> getProfile() {
     *     JwtAuthenticationToken auth = (JwtAuthenticationToken) 
     *         SecurityContextHolder.getContext().getAuthentication();
     *     Long userId = auth.getUserId(); // 当前用户ID
     *     // 查询用户信息...
     * }
     * </pre>
     * 
     * @return 用户ID
     */
    public long getUserId() {
        return userInfo.userId();
    }

    /**
     * 获取完整用户信息
     * 
     * <p>返回JwtUserInfo对象，包含用户ID和显示名称。
     * 比getUserId()提供更完整的用户信息。
     * 
     * @return 用户信息对象（不可变）
     */
    public JwtUserInfo getUserInfo() {
        return userInfo;
    }

    /**
     * 获取凭证（Spring Security接口要求）
     * 
     * <p>返回原始JWT令牌字符串。
     * 在Authentication接口中，credentials通常用于验证用户身份（如密码）。
     * 在JWT认证中，credentials就是JWT本身。
     * 
     * <p>用途：
     * <ul>
     *   <li>审计日志：记录用户使用的具体令牌</li>
     *   <li>令牌追踪：排查令牌相关问题</li>
     *   <li>调试：开发环境查看完整令牌信息</li>
     * </ul>
     * 
     * @return 原始JWT令牌字符串
     */
    @Override
    public Object getCredentials() {
        return rawToken;
    }

    /**
     * 获取主体（Spring Security接口要求）
     * 
     * <p>返回用户信息对象。
     * 在Authentication接口中，principal代表"认证主体"，即"谁通过了认证"。
     * 
     * <p>使用场景：
     * <pre>
     * Authentication auth = SecurityContextHolder.getContext().getAuthentication();
     * Object principal = auth.getPrincipal();
     * if (principal instanceof JwtUserInfo) {
     *     JwtUserInfo userInfo = (JwtUserInfo) principal;
     *     // 使用用户信息...
     * }
     * </pre>
     * 
     * @return 用户信息对象
     */
    @Override
    public Object getPrincipal() {
        return userInfo;
    }
}
