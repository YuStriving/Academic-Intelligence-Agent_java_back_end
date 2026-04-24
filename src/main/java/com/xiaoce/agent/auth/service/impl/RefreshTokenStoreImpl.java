package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.service.IRefreshTokenStore;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class RefreshTokenStoreImpl implements IRefreshTokenStore {

    private static final String VALIDATE_SCRIPT =
            "local userInfo = redis.call('HGET', KEYS[1], ARGV[1])\n" +
                    "if not userInfo then return 0 end\n" +
                    "local sep = string.find(userInfo, '|')\n" +
                    "if not sep then return 0 end\n" +
                    "local userId = string.sub(userInfo, 1, sep - 1)\n" +
                    "if userId == ARGV[2] then return 1 else return 0 end";

    private static final String DEL_SCRIPT =
            "redis.call('HDEL', KEYS[1], ARGV[1])\n" +
                    "redis.call('SREM', KEYS[2], ARGV[1])\n" +
                    "redis.call('DEL', KEYS[3])\n" +
                    "return 1";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveRefreshToken(Long userId, String jti, Instant expireAt) {
        long ttlSeconds = Math.max(1L, Duration.between(Instant.now(), expireAt).toSeconds());
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            String compressedUserInfo = userId + "|" + System.currentTimeMillis();

            connection.hashCommands().hSet(
                    MAIN_HASH_KEY_PREFIX.getBytes(StandardCharsets.UTF_8),
                    jti.getBytes(StandardCharsets.UTF_8),
                    compressedUserInfo.getBytes(StandardCharsets.UTF_8)
            );

            connection.setCommands().sAdd(
                    userSetKey(userId).getBytes(StandardCharsets.UTF_8),
                    jti.getBytes(StandardCharsets.UTF_8)
            );

            connection.stringCommands().setEx(
                    jtiExpireKey(jti).getBytes(StandardCharsets.UTF_8),
                    ttlSeconds,
                    "1".getBytes(StandardCharsets.UTF_8)
            );

            return null;
        });
    }

    @Override
    public boolean validateRefreshToken(Long userId, String jti) {
        if (userId == null || jti == null) {
            throw new IllegalArgumentException("userId and jti cannot be null");
        }

        Boolean hasExpireKey = redisTemplate.hasKey(jtiExpireKey(jti));
        if (hasExpireKey == null || !hasExpireKey) {
            return false;
        }

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(VALIDATE_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(MAIN_HASH_KEY_PREFIX),
                jti,
                String.valueOf(userId)
        );
        return result != null && result == 1;
    }

    @Override
    public void removeRefreshToken(Long userId, String jti) {
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
    }

    @Override
    public void removeUserAllRefreshToken(Long userId) {
        String userSetKey = userSetKey(userId);
        Set<String> jtiSet = redisTemplate.opsForSet().members(userSetKey);
        if (jtiSet == null || jtiSet.isEmpty()) {
            return;
        }

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] userSetKeyBytes = userSetKey.getBytes(StandardCharsets.UTF_8);
            byte[] mainHashKeyBytes = MAIN_HASH_KEY_PREFIX.getBytes(StandardCharsets.UTF_8);

            for (String jti : jtiSet) {
                byte[] jtiBytes = jti.getBytes(StandardCharsets.UTF_8);
                connection.hashCommands().hDel(mainHashKeyBytes, jtiBytes);
                connection.keyCommands().del(jtiExpireKey(jti).getBytes(StandardCharsets.UTF_8));
            }
            connection.keyCommands().del(userSetKeyBytes);
            return null;
        });
    }

    private String userSetKey(long userId) {
        return USER_SET_KEY_PREFIX + userId + ":tokens";
    }

    private String jtiExpireKey(String jti) {
        return TOKEN_KEY_PREFIX + jti;
    }
}
