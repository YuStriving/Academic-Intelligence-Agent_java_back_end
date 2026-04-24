package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.service.IRefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupTask {

    private static final String CLEANUP_LOCK_KEY = "auth:refresh:cleanup:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(55);
    private static final int DEFAULT_BATCH_SIZE = 300;
    private static final int MAX_BATCH_ROUNDS = 10;

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = buildReleaseLockScript();

    private final StringRedisTemplate redisTemplate;
    private final IRefreshTokenStore refreshTokenStore;

    private final String workerId = UUID.randomUUID().toString();

    @Scheduled(
            fixedDelayString = "${auth.refresh.cleanup.fixed-delay-ms:60000}",
            initialDelayString = "${auth.refresh.cleanup.initial-delay-ms:30000}"
    )
    public void cleanupStaleRefreshTokens() {
        if (!tryAcquireLock()) {
            return;
        }

        long start = System.currentTimeMillis();
        int totalCleaned = 0;
        int rounds = 0;
        try {
            for (int i = 0; i < MAX_BATCH_ROUNDS; i++) {
                int cleaned = refreshTokenStore.cleanupExpiredTokens(DEFAULT_BATCH_SIZE);
                totalCleaned += cleaned;
                rounds++;
                if (cleaned < DEFAULT_BATCH_SIZE) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.error("refresh token cleanup task failed", ex);
        } finally {
            releaseLock();
        }

        if (totalCleaned > 0) {
            log.info("refresh token cleanup finished - cleaned: {}, rounds: {}, cost: {}ms",
                    totalCleaned, rounds, System.currentTimeMillis() - start);
        }
    }

    private boolean tryAcquireLock() {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(CLEANUP_LOCK_KEY, workerId, LOCK_TTL);
        return Boolean.TRUE.equals(locked);
    }

    private void releaseLock() {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(CLEANUP_LOCK_KEY), workerId);
    }

    private static DefaultRedisScript<Long> buildReleaseLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('DEL', KEYS[1]) " +
                        "else return 0 end"
        );
        return script;
    }
}
