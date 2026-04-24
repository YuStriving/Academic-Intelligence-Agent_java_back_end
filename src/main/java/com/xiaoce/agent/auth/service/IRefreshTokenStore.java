package com.xiaoce.agent.auth.service;

import java.time.Instant;
import java.util.Set;

/**
 * 刷新令牌存储接口
 * 
 * <p>管理用户刷新令牌的生命周期，包括保存、验证、删除等操作。
 * 采用惰性删除策略，在访问时自动清理过期令牌。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>用户登录成功后保存刷新令牌</li>
 *   <li>刷新令牌时验证令牌有效性</li>
 *   <li>用户登出时删除刷新令牌</li>
 *   <li>全设备登出时删除用户所有令牌</li>
 * </ul>
 * 
 * @author 小策
 * @date 2026/4/22 9:52
 */
public interface IRefreshTokenStore {

    /**
     * 保存刷新令牌
     * 
     * @param userId 用户ID
     * @param tokenId 令牌ID (jti)
     * @param ttl 过期时间
     */
    void saveRefreshToken(Long userId, String tokenId, Instant ttl);

    /**
     * 验证刷新令牌
     * 
     * <p>使用Lua脚本原子性检查：
     * <ol>
     *   <li>过期标记是否存在</li>
     *   <li>令牌是否归属于该用户</li>
     * </ol>
     * 
     * @param userId 用户ID
     * @param tokenId 令牌ID (jti)
     * @return 令牌是否有效
     */
    boolean validateRefreshToken(Long userId, String tokenId);

    /**
     * 删除单个刷新令牌
     * 
     * <p>使用Lua脚本原子性删除令牌在Hash、Set和过期key中的所有数据。
     * 
     * @param userId 用户ID
     * @param tokenId 令牌ID (jti)
     */
    void removeRefreshToken(Long userId, String tokenId);

    /**
     * 删除用户所有刷新令牌
     * 
     * <p>用于全设备登出场景，删除该用户的所有刷新令牌。
     * 
     * @param userId 用户ID
     */
    void removeUserAllRefreshToken(Long userId);

    /**
     * 获取用户有效的刷新令牌列表（带惰性清理）
     * 
     * <p>在获取用户令牌列表时，自动清理已过期的令牌。
     * 这是方案C的核心方法，实现惰性删除策略。
     * 
     * <p>工作流程：
     * <ol>
     *   <li>获取用户Set中的所有令牌ID</li>
     *   <li>检查每个令牌的过期标记是否存在</li>
     *   <li>将过期令牌从Set和Hash中删除</li>
     *   <li>返回剩余的有效令牌ID</li>
     * </ol>
     * 
     * <p>使用场景：
     * <ul>
     *   <li>全设备登出时需要获取用户所有令牌</li>
     *   <li>定期维护用户令牌数据</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @return 有效的令牌ID集合
     */
    Set<String> getUserValidTokens(Long userId);

    /**
     * 增量清理全局过期refresh token索引
     *
     * @param batchSize 单次清理条数
     * @return 实际清理条数
     */
    int cleanupExpiredTokens(int batchSize);
}
