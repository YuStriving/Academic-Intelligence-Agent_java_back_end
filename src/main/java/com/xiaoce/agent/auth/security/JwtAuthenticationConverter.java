package com.xiaoce.agent.auth.security;

import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import com.xiaoce.agent.auth.service.impl.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.xiaoce.agent.auth.common.constant.RefreshToken.USER_TOKEN_VERSION_KEY_PREFIX;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private final JwtTokenService jwtTokenService;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        try {
            if (!JwtTokenService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE))) {
                throw new BadCredentialsException("Invalid access token");
            }

            Long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
            long currentVersion = getCurrentTokenVersion(userId);
            jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_ACCESS);

            String nickname = jwt.getClaimAsString("nickName");
            JwtUserInfo userInfo = new JwtUserInfo(userId, nickname);
            return new JwtAuthenticationToken(userInfo, jwt.getTokenValue());
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid access token", e);
        }
    }

    private long getCurrentTokenVersion(Long userId) {
        String raw = redisTemplate.opsForValue().get(USER_TOKEN_VERSION_KEY_PREFIX + userId);
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
