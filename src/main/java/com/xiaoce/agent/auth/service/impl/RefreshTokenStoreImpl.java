package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.enums.ClientType;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.GLOBAL_EXPIRY_ZSET_KEY;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.TOKEN_KEY_PREFIX;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.USER_SET_KEY_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenStoreImpl implements IRefreshTokenStore {

    private static final String USER_TOKEN_KEY_SUFFIX = ":tokens";
    private static final String GLOBAL_MEMBER_SEPARATOR = "|";

    private final StringRedisTemplate redisTemplate;

    private static DefaultRedisScript<Long> longScript(String scriptText) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        return script;
    }

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = longScript(
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])\n" +
            "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[4])\n" +
            "redis.call('ZADD', KEYS[3], ARGV[2], ARGV[5])\n" +
            "local currentTtl = redis.call('TTL', KEYS[2])\n" +
            "local desiredTtl = tonumber(ARGV[3])\n" +
            "if currentTtl < desiredTtl then redis.call('EXPIRE', KEYS[2], desiredTtl) end\n" +
            "local maxPerClient = tonumber(ARGV[7]) or 1\n" +
            "local allMembers = redis.call('ZRANGE', KEYS[2], 0, -1)\n" +
            "local groups = {}\n" +
            "for _, member in ipairs(allMembers) do\n" +
            "  local prefix = string.match(member, '^(.-)|')\n" +
            "  if prefix then\n" +
            "    if not groups[prefix] then groups[prefix] = {} end\n" +
            "    table.insert(groups[prefix], member)\n" +
            "  end\n" +
            "end\n" +
            "for clientType, members in pairs(groups) do\n" +
            "  local count = #members\n" +
            "  if count > maxPerClient then\n" +
            "    local removeCount = count - maxPerClient\n" +
            "    for i = 1, removeCount do\n" +
            "      local oldMember = members[i]\n" +
            "      redis.call('ZREM', KEYS[2], oldMember)\n" +
            "      local oldJti = string.match(oldMember, '|(.+)$')\n" +
            "      if oldJti then\n" +
            "        local oldTokenKey = ARGV[6] .. clientType .. ':' .. oldJti\n" +
            "        redis.call('DEL', oldTokenKey)\n" +
            "        redis.call('ZREM', KEYS[3], ARGV[1] .. '|' .. oldJti)\n" +
            "      end\n" +
            "    end\n" +
            "  end\n" +
            "end\n" +
            "return 1"
    );

    private static final DefaultRedisScript<Long> VALIDATE_SCRIPT = longScript(
            "local owner = redis.call('GET', KEYS[1])\n" +
            "if not owner then\n" +
            "  redis.call('ZREM', KEYS[2], ARGV[2])\n" +
            "  redis.call('ZREM', KEYS[3], ARGV[3])\n" +
            "  return 0\n" +
            "end\n" +
            "local score = redis.call('ZSCORE', KEYS[2], ARGV[2])\n" +
            "if not score then\n" +
            "  redis.call('DEL', KEYS[1])\n" +
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

    @Override
    public void saveRefreshToken(Long userId, String jti, Instant expireAt, ClientType clientType) {
        requireArgs(userId, jti, expireAt);

        long ttlSeconds = Math.max(1L, Duration.between(Instant.now(), expireAt).toSeconds());
        long expireAtEpochSeconds = expireAt.getEpochSecond();

        Long saved = redisTemplate.execute(
                SAVE_SCRIPT,
                Arrays.asList(jtiTokenKey(userId.toString(), jti, clientType), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(userId),
                String.valueOf(expireAtEpochSeconds),
                String.valueOf(ttlSeconds),
                userSetMember(clientType.getCode(), jti),
                globalMember(userId, jti),
                jwtTokenPrefix(userId.toString()),
                "1"
        );

        if (saved == null || saved != 1L) {
            throw new IllegalStateException("保存刷新令牌失败");
        }
    }

    @Override
    public boolean validateRefreshToken(Long userId, String jti, ClientType clientType) {
        requireArgs(userId, jti, Instant.now());

        Long result = redisTemplate.execute(
                VALIDATE_SCRIPT,
                Arrays.asList(jtiTokenKey(userId.toString(), jti, clientType), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(userId),
                userSetMember(clientType.getCode(), jti),
                globalMember(userId, jti)
        );

        return result != null && result == 1L;
    }

    @Override
    public boolean removeRefreshToken(Long userId, String jti, ClientType clientType) {
        requireArgs(userId, jti, Instant.now());

        Long result = redisTemplate.execute(
                REMOVE_SCRIPT,
                Arrays.asList(jtiTokenKey(userId.toString(), jti, clientType), userTokenZsetKey(userId), GLOBAL_EXPIRY_ZSET_KEY),
                String.valueOf(userId),
                userSetMember(clientType.getCode(), jti),
                globalMember(userId, jti)
        );

        if (result != null && result == -1L) {
            log.warn("删除的不是当前用户所拥有的令牌 - userId: {}, jti: {}", userId, jti);
            return false;
        }
        return result != null && result == 1L;
    }

    @Override
    public void removeUserAllRefreshToken(Long userId, ClientType clientType) {
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

            for (String tokenMember : allTokens) {
                String memberClientType = extractClientTypeFromMember(tokenMember);
                String memberJti = extractJtiFromMember(tokenMember);
                if (!StringUtils.hasText(memberClientType) || !StringUtils.hasText(memberJti)) {
                    continue;
                }
                ClientType memberType = ClientType.fromCode(memberClientType);
                if (memberType == null) {
                    continue;
                }
                connection.keyCommands().del(jtiTokenKey(userId.toString(), memberJti, memberType).getBytes(StandardCharsets.UTF_8));
                connection.zSetCommands().zRem(globalKeyBytes, globalMember(userId, memberJti).getBytes(StandardCharsets.UTF_8));
            }

            connection.keyCommands().del(userKeyBytes);
            return null;
        });
    }

    @Override
    public int cleanupExcessTokens(Long userId, ClientType clientType, int maxToKeep) {
        if (userId == null || maxToKeep <= 0) {
            throw new IllegalArgumentException("userId cannot be null and maxToKeep must be greater than 0");
        }

        String userZsetKey = userTokenZsetKey(userId);
        Set<String> allTokens = redisTemplate.opsForZSet().range(userZsetKey, 0, -1);

        if (allTokens == null || allTokens.isEmpty()) {
            return 0;
        }

        String clientPrefix = clientType.getCode() + "|";
        List<String> clientTokens = allTokens.stream()
                .filter(token -> token.startsWith(clientPrefix))
                .collect(Collectors.toList());

        if (clientTokens.size() <= maxToKeep) {
            return 0;
        }

        int toRemoveCount = clientTokens.size() - maxToKeep;
        int removedCount = 0;

        for (int i = 0; i < toRemoveCount; i++) {
            String oldTokenMember = clientTokens.get(i);
            String oldJti = extractJtiFromMember(oldTokenMember);
            if (!StringUtils.hasText(oldJti)) {
                continue;
            }
            try {
                boolean removed = removeRefreshToken(userId, oldJti, clientType);
                if (removed) {
                    removedCount++;
                }
            } catch (Exception e) {
                log.warn("清理旧令牌失败 - userId: {}, jti: {}, error: {}", userId, oldJti, e.getMessage());
            }
        }

        return removedCount;
    }

    private static void requireArgs(Long userId, String jti, Instant instant) {
        if (userId == null || !StringUtils.hasText(jti) || instant == null) {
            throw new IllegalArgumentException("userId/jti/expireAt cannot be null");
        }
    }

    private String userTokenZsetKey(Long userId) {
        return USER_SET_KEY_PREFIX + userId + USER_TOKEN_KEY_SUFFIX;
    }

    private String jtiTokenKey(String userId, String jti, ClientType type) {
        return TOKEN_KEY_PREFIX + userId + ":" + "slot:" + type.getCode() + ":" + jti;
    }

    private String jwtTokenPrefix(String userId) {
        return TOKEN_KEY_PREFIX + userId + ":" + "slot:";
    }

    private String globalMember(Long userId, String jti) {
        return userId + GLOBAL_MEMBER_SEPARATOR + jti;
    }

    private String userSetMember(String clientType, String jti) {
        return clientType + GLOBAL_MEMBER_SEPARATOR + jti;
    }

    private String extractClientTypeFromMember(String member) {
        if (!StringUtils.hasText(member)) {
            return null;
        }
        int sep = member.indexOf(GLOBAL_MEMBER_SEPARATOR);
        if (sep <= 0) {
            return null;
        }
        return member.substring(0, sep);
    }

    private String extractJtiFromMember(String member) {
        if (!StringUtils.hasText(member)) {
            return null;
        }
        int sep = member.indexOf(GLOBAL_MEMBER_SEPARATOR);
        if (sep < 0 || sep >= member.length() - 1) {
            return null;
        }
        return member.substring(sep + 1);
    }
}