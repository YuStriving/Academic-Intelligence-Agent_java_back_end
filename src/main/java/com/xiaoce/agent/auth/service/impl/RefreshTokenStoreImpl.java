package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.service.IRefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static com.xiaoce.agent.auth.common.constant.RefreshToken.MAIN_HASH_KEY_PREFIX;
import static com.xiaoce.agent.auth.common.constant.RefreshToken.TOKEN_KEY_PREFIX;
import static com.xiaoce.agent.auth.common.constant.RefreshToken.USER_SET_KEY_PREFIX;

/**
 * 刷新令牌Redis存储实现
 * 
 * <p>使用Redis存储和管理用户的刷新令牌，支持令牌的保存、验证、删除和全设备登出功能。
 * 使用Redis的Hash、Set和String三种数据结构，保证操作的原子性和高效性。
 * 
 * <p>数据结构：
 * <ul>
 *   <li>Hash: 存储令牌ID到用户ID的映射</li>
 *   <li>Set: 存储用户拥有的所有令牌ID</li>
 *   <li>String: 存储令牌ID到过期标记，带有过期时间</li>
 * </ul>
 * 
 * <p>使用场景：
 * <ul>
 *   <li>用户登录成功后保存刷新令牌</li>
 *   <li>刷新令牌时验证令牌有效性</li>
 *   <li>用户登出时删除刷新令牌</li>
 *   <li>全设备登出时删除用户所有令牌</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenStoreImpl implements IRefreshTokenStore {

    /**
     * 验证刷新令牌的Lua脚本
     * 
     * <p>原子性地从Hash中获取令牌对应的用户ID，并验证是否匹配。
     * 如果验证成功返回1，否则返回0。
     */
    private static final String VALIDATE_SCRIPT =
            "local userInfo = redis.call('HGET', KEYS[1], ARGV[1])\n" +
            "if not userInfo then return 0 end\n" +
            "local sep = string.find(userInfo, '|')\n" +
            "if not sep then return 0 end\n" +
            "local userId = string.sub(userInfo, 1, sep - 1)\n" +
            "if userId == ARGV[2] then return 1 else return 0 end";

    /**
     * 删除刷新令牌的Lua脚本
     * 
     * <p>原子性地从Hash、Set和String中删除令牌相关数据。
     * 返回1表示成功。
     */
    private static final String DEL_SCRIPT =
            "redis.call('HDEL', KEYS[1], ARGV[1])\n" +
            "redis.call('SREM', KEYS[2], ARGV[1])\n" +
            "redis.call('DEL', KEYS[3])\n" +
            "return 1";

    private final StringRedisTemplate redisTemplate;

    /**
     * 保存刷新令牌
     * 
     * <p>原子性地将刷新令牌信息保存到Redis的三个数据结构中：
     * <ul>
     *   <li>Hash: 令牌ID -> 用户ID|时间戳</li>
     *   <li>Set: 用户ID -> 令牌ID集合</li>
     *   <li>String: 令牌ID -> "1" (带过期时间)</li>
     * </ul>
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户登录成功后保存刷新令牌</li>
     *   <li>刷新令牌成功后保存新的刷新令牌</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @param jti 令牌ID
     * @param expireAt 过期时间
     */
    @Override
    public void saveRefreshToken(Long userId, String jti, Instant expireAt) {
        // 计算过期秒数，确保至少为1秒
        long ttlSeconds = Math.max(1L, Duration.between(Instant.now(), expireAt).toSeconds());
        
        // 使用Redis管道原子性地执行多个操作
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            String compressedUserInfo = userId + "|" + System.currentTimeMillis();

            // 1. 保存到Hash: 令牌ID -> 用户信息
            connection.hashCommands().hSet(
                    MAIN_HASH_KEY_PREFIX.getBytes(StandardCharsets.UTF_8),
                    jti.getBytes(StandardCharsets.UTF_8),
                    compressedUserInfo.getBytes(StandardCharsets.UTF_8)
            );

            // 2. 保存到Set: 用户ID -> 令牌ID集合
            connection.setCommands().sAdd(
                    userSetKey(userId).getBytes(StandardCharsets.UTF_8),
                    jti.getBytes(StandardCharsets.UTF_8)
            );

            // 3. 保存到String: 令牌ID -> "1" (带过期时间)
            connection.stringCommands().setEx(
                    jtiExpireKey(jti).getBytes(StandardCharsets.UTF_8),
                    ttlSeconds,
                    "1".getBytes(StandardCharsets.UTF_8)
            );

            return null;
        });

        log.debug("刷新令牌保存成功 - 用户ID: {}, 令牌ID: {}, 过期时间: {}s", 
                userId, jti, ttlSeconds);
    }

    /**
     * 验证刷新令牌
     * 
     * <p>验证刷新令牌是否有效，分为两步：
     * <ol>
     *   <li>检查String类型的过期标记是否存在</li>
     *   <li>使用Lua脚本原子性地验证Hash中的用户ID是否匹配</li>
     * </ol>
     * 
     * <p>使用场景：
     * <ul>
     *   <li>刷新令牌时验证令牌是否有效</li>
     *   <li>用户登出时验证令牌是否有效</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @param jti 令牌ID
     * @return 令牌是否有效
     */
    @Override
    public boolean validateRefreshToken(Long userId, String jti) {
        if (userId == null || jti == null) {
            throw new IllegalArgumentException("userId and jti cannot be null");
        }

        // 先检查过期标记是否存在
        Boolean hasExpireKey = redisTemplate.hasKey(jtiExpireKey(jti));
        if (hasExpireKey == null || !hasExpireKey) {
            log.debug("刷新令牌验证失败 - 过期标记不存在, 用户ID: {}, 令牌ID: {}", 
                    userId, jti);
            return false;
        }

        // 使用Lua脚本原子性地验证令牌
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(VALIDATE_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(MAIN_HASH_KEY_PREFIX),
                jti,
                String.valueOf(userId)
        );
        
        boolean isValid = result != null && result == 1;
        
        log.debug("刷新令牌验证结果 - 用户ID: {}, 令牌ID: {}, 结果: {}", 
                userId, jti, isValid);
        
        return isValid;
    }

    /**
     * 删除刷新令牌
     * 
     * <p>原子性地从Redis的三个数据结构中删除刷新令牌相关数据：
     * <ul>
     *   <li>从Hash中删除令牌ID到用户信息的映射</li>
     *   <li>从Set中删除用户拥有的令牌ID</li>
     *   <li>从String中删除过期标记</li>
     * </ul>
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户登出时删除当前设备的令牌</li>
     *   <li>刷新令牌时删除旧的刷新令牌</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @param jti 令牌ID
     */
    @Override
    public void removeRefreshToken(Long userId, String jti) {
        // 使用Lua脚本原子性地执行多个删除操作
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(DEL_SCRIPT);
        script.setResultType(Long.class);
        
        redisTemplate.execute(
                script,
                Arrays.asList(
                        MAIN_HASH_KEY_PREFIX,
                        userSetKey(userId),
                        jtiExpireKey(jti)
                ),
                jti
        );
        
        log.debug("刷新令牌删除成功 - 用户ID: {}, 令牌ID: {}", userId, jti);
    }

    /**
     * 删除用户所有刷新令牌
     * 
     * <p>删除用户的所有刷新令牌，使用Redis管道原子性地执行多个操作：
     * <ol>
     *   <li>获取用户拥有的所有令牌ID</li>
     *   <li>从Hash中删除所有令牌ID的映射</li>
     *   <li>从String中删除所有过期标记</li>
     *   <li>删除用户的令牌集合</li>
     * </ol>
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户全设备登出</li>
     *   <li>管理员封禁用户</li>
     * </ul>
     * 
     * @param userId 用户ID
     */
    @Override
    public void removeUserAllRefreshToken(Long userId) {
        String userSetKey = userSetKey(userId);
        Set<String> jtiSet = redisTemplate.opsForSet().members(userSetKey);
        if (jtiSet == null || jtiSet.isEmpty()) {
            log.debug("用户没有刷新令牌需要删除 - 用户ID: {}", userId);
            return;
        }

        // 使用Redis管道原子性地执行多个删除操作
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] userSetKeyBytes = userSetKey.getBytes(StandardCharsets.UTF_8);
            byte[] mainHashKeyBytes = MAIN_HASH_KEY_PREFIX.getBytes(StandardCharsets.UTF_8);

            // 1. 从Hash中删除所有令牌ID的映射
            for (String jti : jtiSet) {
                byte[] jtiBytes = jti.getBytes(StandardCharsets.UTF_8);
                connection.hashCommands().hDel(mainHashKeyBytes, jtiBytes);
                connection.keyCommands().del(jtiExpireKey(jti).getBytes(StandardCharsets.UTF_8));
            }
            
            // 2. 删除用户的令牌集合
            connection.keyCommands().del(userSetKeyBytes);
            
            return null;
        });
        
        log.info("用户所有刷新令牌删除成功 - 用户ID: {}, 令牌数量: {}", 
                userId, jtiSet.size());
    }

    /**
     * 构建用户令牌集合的Redis键
     * 
     * @param userId 用户ID
     * @return Redis键
     */
    private String userSetKey(long userId) {
        return USER_SET_KEY_PREFIX + userId + ":tokens";
    }

    /**
     * 构建令牌过期标记的Redis键
     * 
     * @param jti 令牌ID
     * @return Redis键
     */
    private String jtiExpireKey(String jti) {
        return TOKEN_KEY_PREFIX + jti;
    }
}
