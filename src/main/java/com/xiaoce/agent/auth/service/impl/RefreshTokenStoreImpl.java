package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.service.IRefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.GLOBAL_EXPIRY_ZSET_KEY;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.TOKEN_KEY_PREFIX;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.USER_SET_KEY_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenStoreImpl implements IRefreshTokenStore {

    private static final String USER_TOKEN_KEY_SUFFIX = ":tokens";
    private static final String GLOBAL_MEMBER_SEPARATOR = "|";
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 200;

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = longScript(
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])\n" +
            "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[4])\n" +
            "redis.call('ZADD', KEYS[3], ARGV[2], ARGV[5])\n" +
            "local currentTtl = redis.call('TTL', KEYS[2])\n" +
            "local desiredTtl = tonumber(ARGV[3])\n" +
            "if currentTtl < desiredTtl then redis.call('EXPIRE', KEYS[2], desiredTtl) end\n" +
            "return 1"
    );

    private static final DefaultRedisScript<Long> VALIDATE_SCRIPT = longScript(
            "local owner = redis.call('GET', KEYS[1])\n" +
            "if not owner then\n" +
            "  redis.call('ZREM', KEYS[2], ARGV[2])\n" +
            "  redis.call('ZREM', KEYS[3], ARGV[3])\n" +
            "  return 0\n" +
            "end\n" +
            "if owner ~= ARGV[1] then return 0 end\n" +
            "return 1"
    );

    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = longScript(
            "local owner = redis.call('GET', KEYS[1])\n" +
            "if owner and owner ~= ARGV[1] then return -1 end\n" +
            "redis.call('DEL', KEYS[1])\n" +
            "redis.call('ZREM', KEYS[2], ARGV[2])\n" +
            "redis.call('ZREM', KEYS[3], ARGV[3])\n" +
            "if owner then return 1 else return 0 end"
    );

    private static final DefaultRedisScript<Long> CLEANUP_BATCH_SCRIPT = longScript(
            "local members = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])\n" +
            "for _, member in ipairs(members) do\n" +
            "  local sep = string.find(member, ARGV[6], 1, true)\n" +
            "  if sep then\n" +
            "    local userId = string.sub(member, 1, sep - 1)\n" +
            "    local jti = string.sub(member, sep + 1)\n" +
            "    redis.call('DEL', ARGV[3] .. jti)\n" +
            "    redis.call('ZREM', ARGV[4] .. userId .. ARGV[5], jti)\n" +
            "  end\n" +
            "  redis.call('ZREM', KEYS[1], member)\n" +
            "end\n" +
            "return #members"
    );

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveRefreshToken(Long userId, String jti, Instant expireAt) {
        requireArgs(userId, jti, expireAt);

        long ttlSeconds = Math.max(1L, Duration.between(Instant.now(), expireAt).toSeconds());
        long expireAtEpochSeconds = expireAt.getEpochSecond();

        Long saved = redisTemplate.execute(
                SAVE_SCRIPT,
                Arrays.asList(jtiTokenKey(jti), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(userId),
                String.valueOf(expireAtEpochSeconds),
                String.valueOf(ttlSeconds),
                jti,
                globalMember(userId, jti)
        );

        if (saved == null || saved != 1L) {
            throw new IllegalStateException("Failed to save refresh token");
        }
    }

    @Override
    public boolean validateRefreshToken(Long userId, String jti) {
        requireArgs(userId, jti, Instant.now());

        Long result = redisTemplate.execute(
                VALIDATE_SCRIPT,
                Arrays.asList(jtiTokenKey(jti), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(userId),
                jti,
                globalMember(userId, jti)
        );
        return result != null && result == 1L;
    }

    @Override
    public void removeRefreshToken(Long userId, String jti) {
        requireArgs(userId, jti, Instant.now());

        Long result = redisTemplate.execute(
                REMOVE_SCRIPT,
                Arrays.asList(jtiTokenKey(jti), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(userId),
                jti,
                globalMember(userId, jti)
        );

        if (result != null && result == -1L) {
            log.warn("Skip removing refresh token due to owner mismatch - userId: {}, jti: {}", userId, jti);
        }
    }

    @Override
    public void removeUserAllRefreshToken(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }

        String userZsetKey = userTokenZsetKey(userId);
        Set<String> allTokens = redisTemplate.opsForZSet().range(userZsetKey, 0, -1);
        if (allTokens == null || allTokens.isEmpty()) {
            redisTemplate.delete(userZsetKey);
            return;
        }

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] globalKeyBytes = GLOBAL_EXPIRY_ZSET_KEY.getBytes(StandardCharsets.UTF_8);
            byte[] userKeyBytes = userZsetKey.getBytes(StandardCharsets.UTF_8);

            for (String jti : allTokens) {
                connection.keyCommands().del(jtiTokenKey(jti).getBytes(StandardCharsets.UTF_8));
                connection.zSetCommands().zRem(globalKeyBytes, globalMember(userId, jti).getBytes(StandardCharsets.UTF_8));
            }
            connection.keyCommands().del(userKeyBytes);
            return null;
        });
    }

    @Override
    public Set<String> getUserValidTokens(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }

        String userZsetKey = userTokenZsetKey(userId);
        long nowEpochSeconds = Instant.now().getEpochSecond();

        Set<String> expiredJtis = redisTemplate.opsForZSet().rangeByScore(userZsetKey, 0, nowEpochSeconds);
        if (expiredJtis != null && !expiredJtis.isEmpty()) {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                byte[] userKeyBytes = userZsetKey.getBytes(StandardCharsets.UTF_8);
                byte[] globalKeyBytes = GLOBAL_EXPIRY_ZSET_KEY.getBytes(StandardCharsets.UTF_8);

                for (String jti : expiredJtis) {
                    connection.zSetCommands().zRem(userKeyBytes, jti.getBytes(StandardCharsets.UTF_8));
                    connection.zSetCommands().zRem(globalKeyBytes, globalMember(userId, jti).getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
        }

        Set<String> valid = redisTemplate.opsForZSet().rangeByScore(userZsetKey, nowEpochSeconds + 1, Double.POSITIVE_INFINITY);
        return valid == null ? new HashSet<>() : new HashSet<>(valid);
    }

    @Override
    public int cleanupExpiredTokens(int batchSize) {
        int size = batchSize > 0 ? batchSize : DEFAULT_CLEANUP_BATCH_SIZE;
        Long cleaned = redisTemplate.execute(
                CLEANUP_BATCH_SCRIPT,
                Arrays.asList(GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(Instant.now().getEpochSecond()),
                String.valueOf(size),
                TOKEN_KEY_PREFIX,
                USER_SET_KEY_PREFIX,
                USER_TOKEN_KEY_SUFFIX,
                GLOBAL_MEMBER_SEPARATOR
        );
        return cleaned == null ? 0 : cleaned.intValue();
    }

    private static DefaultRedisScript<Long> longScript(String scriptText) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        return script;
    }

    private static void requireArgs(Long userId, String jti, Instant instant) {
        if (userId == null || !StringUtils.hasText(jti) || instant == null) {
            throw new IllegalArgumentException("userId/jti/expireAt cannot be null");
        }
    }

    private String userTokenZsetKey(Long userId) {
        return USER_SET_KEY_PREFIX + userId + USER_TOKEN_KEY_SUFFIX;
    }

    private String jtiTokenKey(String jti) {
        return TOKEN_KEY_PREFIX + jti;
    }

    private String globalMember(Long userId, String jti) {
        return userId + GLOBAL_MEMBER_SEPARATOR + jti;
    }
}
