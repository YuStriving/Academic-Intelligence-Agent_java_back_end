package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.auth.config.AuthProperties;
import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.domain.vo.TokenPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_TOKEN_VERSION = "tv";

    private final AuthProperties authProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    private final Clock clock = Clock.systemUTC();

    public TokenPair issueTokenPair(User user, long tokenVersion) {
        Instant issueAt = Instant.now(clock);
        Instant accessExpiresAt = issueAt.plus(authProperties.getJwt().getAccessTokenTtl());
        Instant refreshExpiresAt = issueAt.plus(authProperties.getJwt().getRefreshTokenTtl());

        String accessTokenId = UUID.randomUUID().toString();
        String refreshTokenId = UUID.randomUUID().toString();

        String accessToken = createAccessToken(accessTokenId, user, issueAt, accessExpiresAt, tokenVersion);
        String refreshToken = createRefreshToken(refreshTokenId, user, issueAt, refreshExpiresAt, tokenVersion);

        return new TokenPair(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt, refreshTokenId);
    }

    public Jwt parseAndVerify(String token, String expectedType) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            validateCommonClaims(jwt, expectedType);
            return jwt;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw invalidTokenError(expectedType);
        }
    }

    public JwtUserInfo extractUserInfo(Jwt jwt) {
        Long userId = parseSubjectAsUserId(jwt.getSubject());
        String nickName = jwt.getClaimAsString("nickName");
        String username = jwt.getClaimAsString("username");
        return new JwtUserInfo(userId, nickName != null ? nickName : username);
    }

    public String extractJwtId(Jwt jwt) {
        return jwt.getId();
    }

    public long extractTokenVersion(Jwt jwt) {
        Object raw = jwt.getClaims().get(CLAIM_TOKEN_VERSION);
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token version");
            }
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token version");
    }

    public void assertTokenVersion(Jwt jwt, long currentVersion, String expectedType) {
        long tokenVersion = extractTokenVersion(jwt);
        if (tokenVersion != currentVersion) {
            throw invalidTokenError(expectedType);
        }
    }

    public Long parseSubjectAsUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token subject");
        }
        try {
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token subject");
            }
            return userId;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid token subject");
        }
    }

    private String createAccessToken(String accessTokenId, User user, Instant issueAt, Instant expireAt, long tokenVersion) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(issueAt)
                .expiresAt(expireAt)
                .id(accessTokenId)
                .subject(String.valueOf(user.getId()))
                .claim("nickName", user.getNickname())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String createRefreshToken(String refreshTokenId, User user, Instant issueAt, Instant expireAt, long tokenVersion) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(issueAt)
                .expiresAt(expireAt)
                .subject(String.valueOf(user.getId()))
                .id(refreshTokenId)
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private void validateCommonClaims(Jwt jwt, String expectedType) {
        String tokenType = jwt.getClaimAsString(CLAIM_TOKEN_TYPE);
        if (!expectedType.equals(tokenType)) {
            throw invalidTokenError(expectedType);
        }

        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (!authProperties.getJwt().getIssuer().equals(issuer)) {
            throw invalidTokenError(expectedType);
        }

        parseSubjectAsUserId(jwt.getSubject());
    }

    private BusinessException invalidTokenError(String expectedType) {
        if (TYPE_REFRESH.equals(expectedType)) {
            return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
