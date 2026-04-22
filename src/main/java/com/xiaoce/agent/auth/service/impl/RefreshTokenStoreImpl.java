package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.config.AuthProperties;
import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RefreshTokenStoreImpl implements RefreshTokenStoreImpl {

    private static final String USER_SET_KEY_PREFIX = "auth:refresh:user:";
    private static final String TOKEN_KEY_PREFIX = "auth:refresh:jti:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    public void save(Long userId, String jti) {
        Duration ttl = authProperties.getJwt().getRefreshTokenTtl();
        redisTemplate.opsForValue().set(tokenKey(jti), String.valueOf(userId), ttl);
        redisTemplate.opsForSet().add(userSetKey(userId), jti);
        redisTemplate.expire(userSetKey(userId), ttl);
    }

    public void validateOwnership(long userId, String jti) {
        String owner = redisTemplate.opsForValue().get(tokenKey(jti));
        if (owner == null || !owner.equals(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    public void revoke(long userId, String jti) {
        redisTemplate.delete(tokenKey(jti));
        redisTemplate.opsForSet().remove(userSetKey(userId), jti);
    }

    public void revokeAllForUser(long userId) {
        String userSetKey = userSetKey(userId);
        Set<String> jtIs = redisTemplate.opsForSet().members(userSetKey);
        if (jtIs != null && !jtIs.isEmpty()) {
            redisTemplate.delete(jtIs.stream().map(this::tokenKey).collect(Collectors.toSet()));
        }
        redisTemplate.delete(userSetKey);
    }

    private String userSetKey(long userId) {
        return USER_SET_KEY_PREFIX + userId + ":tokens";
    }

    private String tokenKey(String jti) {
        return TOKEN_KEY_PREFIX + jti;
    }
}
