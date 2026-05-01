package com.xiaoce.agent.auth.service;

import com.xiaoce.agent.auth.enums.ClientType;

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

 * 此方法用于将用户的刷新令牌信息保存到系统中
 *
     * @param userId 用户ID - 用于标识哪个用户的令牌
     * @param tokenId 令牌ID (jti) - 令牌的唯一标识符
     * @param ttl 过期时间 - 令牌的存活时间，使用Instant类型表示精确时间点
 * @param clientType 客户端类型 - 指定使用此令牌的客户端类型
 *
 * 使用场景：
 * 1. 用户登录成功后生成刷新令牌时调用
 * 2. 需要更新或延长令牌有效期时调用
 *
 * 注意事项：
 * - 同一用户可能会有多个有效的刷新令牌
 * - 每个令牌都应包含完整的用户信息和过期时间
     */
    void saveRefreshToken(Long userId, String tokenId, Instant ttl,ClientType clientType);

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
    boolean validateRefreshToken(Long userId, String tokenId, ClientType clientType);

    /**
     * 删除单个刷新令牌
     * 
     * <p>使用Lua脚本原子性删除令牌在Hash、Set和过期key中的所有数据。
     * 
     * @param userId 用户ID
     * @param tokenId 令牌ID (jti)
     */
    boolean removeRefreshToken(Long userId, String tokenId,ClientType clientType);

    /**
     * 删除用户所有刷新令牌
     * 
     * <p>用于全设备登出场景，删除该用户的所有刷新令牌。
     * 
     * @param userId 用户ID
     */
    void removeUserAllRefreshToken(Long userId,ClientType clientType);

    /**
     * 清理指定客户端类型的超额令牌
     *
     * <p>当同一用户在同一客户端类型下有多个刷新令牌时（如多次登录,刷新令牌），
     * 此方法会删除最旧的令牌，只保留最新的N个。
     * 这可以防止令牌堆积问题。
     *
     * @param userId 用户ID
     * @param clientType 客户端类型（WEB、APP等）
     * @param maxToKeep 保留的最大数量（通常为1或3）
     * @return 实际清理的令牌数量
     */
    int cleanupExcessTokens(Long userId, ClientType clientType, int maxToKeep);
}
