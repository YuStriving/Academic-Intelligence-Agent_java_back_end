package com.xiaoce.agent.auth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.xiaoce.agent.auth.common.constant.RefreshToken.MAIN_HASH_KEY_PREFIX;
import static com.xiaoce.agent.auth.common.constant.RefreshToken.TOKEN_KEY_PREFIX;

/**
 * 刷新令牌过期数据清理任务
 * 
 * <p>定时扫描并清理自然过期但未被主动删除的刷新令牌相关数据，避免Redis数据膨胀。
 * 
 * <p>实现原理：
 * <ul>
 *   <li>扫描所有用户的令牌集合（Set）</li>
 *   <li>对每个令牌ID检查对应的过期标记是否存在</li>
 *   <li>若过期标记不存在，说明令牌已自然过期，从Hash和Set中清理</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupTask {

    private final StringRedisTemplate redisTemplate;

    /**
     * 定时清理过期的刷新令牌数据
     * 
     * <p>每10分钟执行一次，避免在低峰期之外影响性能。
     * 采用扫描方式逐步清理，避免一次性处理大量数据。
     */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void cleanExpiredRefreshMappings() {
        log.info("开始清理过期刷新令牌数据...");
        
        long startTime = System.currentTimeMillis();
        int cleanedCount = 0;
        
        try {
            // 扫描所有用户的令牌集合Key
            Set<String> userSetKeys = redisTemplate.keys("auth:refresh:user:*:tokens");
            if (userSetKeys == null || userSetKeys.isEmpty()) {
                log.info("没有用户令牌集合需要清理");
                return;
            }

            for (String userSetKey : userSetKeys) {
                cleanedCount += cleanUserExpiredTokens(userSetKey);
            }

        } catch (Exception e) {
            log.error("清理过期刷新令牌数据时发生异常", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("清理过期刷新令牌数据完成 - 清理数量: {}, 耗时: {}ms", cleanedCount, duration);
    }

    /**
     * 清理单个用户的过期令牌
     * 
     * @param userSetKey 用户令牌集合Key
     * @return 清理的令牌数量
     */
    private int cleanUserExpiredTokens(String userSetKey) {
        int cleaned = 0;
        
        try {
            // 使用SSCAN而非members，避免大集合一次性加载到内存
            Cursor<String> cursor = redisTemplate.opsForSet().scan(userSetKey, ScanOptions.NONE);
            
            List<String> expiredJtis = new ArrayList<>();
            
            while (cursor.hasNext()) {
                String jti = cursor.next();
                String expireKey = TOKEN_KEY_PREFIX + jti;
                
                // 检查过期标记是否存在
                Boolean hasExpireKey = redisTemplate.hasKey(expireKey);
                
                if (!Boolean.TRUE.equals(hasExpireKey)) {
                    // 过期标记不存在，说明令牌已过期
                    expiredJtis.add(jti);
                }
            }

            if (!expiredJtis.isEmpty()) {
                // 批量清理过期令牌
                String[] jtiArray = expiredJtis.toArray(new String[0]);
                
                // 从Set中移除
                redisTemplate.opsForSet().remove(userSetKey, jtiArray);
                
                // 从Hash中移除
                redisTemplate.opsForHash().delete(MAIN_HASH_KEY_PREFIX, jtiArray);
                
                cleaned += expiredJtis.size();
                
                log.debug("清理用户过期令牌 - 用户集合: {}, 清理数量: {}", userSetKey, expiredJtis.size());
            }

        } catch (Exception e) {
            log.warn("清理用户过期令牌时发生异常 - 用户集合: {}", userSetKey, e);
        }

        return cleaned;
    }
}
