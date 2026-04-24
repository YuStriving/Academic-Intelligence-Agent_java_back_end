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

/**
 * JWT令牌服务
 * 
 * <p>提供JWT令牌的生成、解析、验证和提取信息等功能。
 * 支持访问令牌和刷新令牌两种类型，包含令牌版本验证机制。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>用户登录成功后生成JWT令牌对</li>
 *   <li>访问受保护接口时验证访问令牌</li>
 *   <li>刷新令牌时验证并生成新的令牌对</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    /**
     * 令牌类型声明名称
     */
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    
    /**
     * 访问令牌类型标识
     */
    public static final String TYPE_ACCESS = "access";
    
    /**
     * 刷新令牌类型标识
     */
    public static final String TYPE_REFRESH = "refresh";
    
    /**
     * 用户ID声明名称
     */
    public static final String CLAIM_USER_ID = "uid";
    
    /**
     * 令牌版本声明名称
     */
    public static final String CLAIM_TOKEN_VERSION = "tv";

    private final AuthProperties authProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    private final Clock clock = Clock.systemUTC();

    /**
     * 生成JWT令牌对
     * 
     * <p>同时生成访问令牌和刷新令牌，包含用户信息、令牌版本和过期时间。
     * 访问令牌有效期较短，用于日常接口访问；刷新令牌有效期较长，用于获取新的令牌对。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户注册成功后立即登录</li>
     *   <li>用户登录成功后</li>
     *   <li>刷新令牌成功后生成新的令牌对</li>
     * </ul>
     * 
     * @param user 用户信息
     * @param tokenVersion 令牌版本号
     * @return 包含访问令牌和刷新令牌的令牌对
     */
    public TokenPair issueTokenPair(User user, long tokenVersion) {
        Instant issueAt = Instant.now(clock);
        Instant accessExpiresAt = issueAt.plus(authProperties.getJwt().getAccessTokenTtl());
        Instant refreshExpiresAt = issueAt.plus(authProperties.getJwt().getRefreshTokenTtl());

        // 生成唯一的令牌ID
        String accessTokenId = UUID.randomUUID().toString();
        String refreshTokenId = UUID.randomUUID().toString();

        // 生成访问令牌和刷新令牌
        String accessToken = createAccessToken(accessTokenId, user, issueAt, accessExpiresAt, tokenVersion);
        String refreshToken = createRefreshToken(refreshTokenId, user, issueAt, refreshExpiresAt, tokenVersion);

        log.debug("JWT令牌对生成成功 - 用户ID: {}, 访问令牌ID: {}, 刷新令牌ID: {}", 
                user.getId(), accessTokenId, refreshTokenId);

        return new TokenPair(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt, refreshTokenId);
    }

    /**
     * 解析并验证JWT令牌
     * 
     * <p>验证令牌签名、过期时间、发行者和令牌类型是否正确。
     * 如果验证失败，抛出相应的业务异常。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>访问受保护接口时验证访问令牌</li>
     *   <li>刷新令牌时验证刷新令牌</li>
     * </ul>
     * 
     * @param token JWT令牌字符串
     * @param expectedType 预期的令牌类型
     * @return 解析后的JWT对象
     */
    public Jwt parseAndVerify(String token, String expectedType) {
        try {
            // 解析令牌并验证签名和过期时间
            Jwt jwt = jwtDecoder.decode(token);
            // 验证令牌的其他声明
            validateCommonClaims(jwt, expectedType);
            return jwt;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.debug("JWT令牌验证失败 - 类型: {}, 错误: {}", expectedType, e.getMessage());
            throw invalidTokenError(expectedType);
        }
    }

    /**
     * 从JWT令牌中提取用户信息
     * 
     * <p>解析令牌中的用户ID、昵称等信息，返回JwtUserInfo对象。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>访问受保护接口时获取当前用户信息</li>
     *   <li>记录操作日志时需要用户信息</li>
     * </ul>
     * 
     * @param jwt JWT令牌对象
     * @return 用户信息对象
     */
    public JwtUserInfo extractUserInfo(Jwt jwt) {
        Long userId = parseSubjectAsUserId(jwt.getSubject());
        String nickName = jwt.getClaimAsString("nickName");
        String username = jwt.getClaimAsString("username");
        return new JwtUserInfo(userId, nickName != null ? nickName : username);
    }

    /**
     * 从JWT令牌中提取令牌ID
     * 
     * <p>获取JWT的jti(JWT ID)声明，用于标识唯一的令牌实例。
     * 
     * @param jwt JWT令牌对象
     * @return 令牌ID
     */
    public String extractJwtId(Jwt jwt) {
        return jwt.getId();
    }

    /**
     * 从JWT令牌中提取令牌版本号
     * 
     * <p>获取JWT中的tv(token version)声明，用于实现全设备登出功能。
     * 如果令牌中没有版本号，默认返回0。
     * 
     * @param jwt JWT令牌对象
     * @return 令牌版本号
     */
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

    /**
     * 验证令牌版本是否有效
     * 
     * <p>比较令牌中的版本号和当前版本号，如果不匹配则说明该令牌已被全设备登出，
     * 用户需要重新登录。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>访问受保护接口时验证令牌是否还生效</li>
     *   <li>刷新令牌时验证令牌是否有效</li>
     * </ul>
     * 
     * @param jwt JWT令牌对象
     * @param currentVersion 当前令牌版本号
     * @param expectedType 令牌类型
     */
    public void assertTokenVersion(Jwt jwt, long currentVersion, String expectedType) {
        long tokenVersion = extractTokenVersion(jwt);
        if (tokenVersion != currentVersion) {
            log.debug("JWT令牌版本不匹配 - 期望: {}, 实际: {}, 类型: {}", 
                    currentVersion, tokenVersion, expectedType);
            throw invalidTokenError(expectedType);
        }
    }

    /**
     * 解析令牌主题为用户ID
     * 
     * <p>从JWT的subject声明中解析用户ID，并验证其合法性。
     * 
     * @param subject JWT的subject声明值
     * @return 用户ID
     */
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

    /**
     * 创建访问令牌
     * 
     * <p>构建访问令牌的声明集，包含用户ID、用户名、昵称、令牌类型、令牌版本等信息，
     * 然后使用JwtEncoder编码为JWT令牌字符串。
     * 
     * @param tokenId 令牌ID
     * @param user 用户信息
     * @param issueAt 发行时间
     * @param expireAt 过期时间
     * @param tokenVersion 令牌版本号
     * @return JWT访问令牌字符串
     */
    private String createAccessToken(String tokenId, User user, Instant issueAt, Instant expireAt, long tokenVersion) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(issueAt)
                .expiresAt(expireAt)
                .id(tokenId)
                .subject(String.valueOf(user.getId()))
                .claim("nickName", user.getNickname())
                .claim("username", user.getUsername())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * 创建刷新令牌
     * 
     * <p>构建刷新令牌的声明集，包含用户ID、令牌类型、令牌版本等信息，
     * 然后使用JwtEncoder编码为JWT令牌字符串。
     * 
     * @param tokenId 令牌ID
     * @param user 用户信息
     * @param issueAt 发行时间
     * @param expireAt 过期时间
     * @param tokenVersion 令牌版本号
     * @return JWT刷新令牌字符串
     */
    private String createRefreshToken(String tokenId, User user, Instant issueAt, Instant expireAt, long tokenVersion) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(issueAt)
                .expiresAt(expireAt)
                .subject(String.valueOf(user.getId()))
                .id(tokenId)
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * 验证JWT令牌的公共声明
     * 
     * <p>验证令牌类型、发行者和主题是否合法。
     * 
     * @param jwt JWT令牌对象
     * @param expectedType 期望的令牌类型
     */
    private void validateCommonClaims(Jwt jwt, String expectedType) {
        // 验证令牌类型
        String tokenType = jwt.getClaimAsString(CLAIM_TOKEN_TYPE);
        if (!expectedType.equals(tokenType)) {
            throw invalidTokenError(expectedType);
        }

        // 验证发行者
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (!authProperties.getJwt().getIssuer().equals(issuer)) {
            throw invalidTokenError(expectedType);
        }

        // 验证主题是否有效
        parseSubjectAsUserId(jwt.getSubject());
    }

    /**
     * 创建无效令牌的业务异常
     * 
     * <p>根据令牌类型返回相应的异常对象。
     * 
     * @param expectedType 令牌类型
     * @return 业务异常对象
     */
    private BusinessException invalidTokenError(String expectedType) {
        if (TYPE_REFRESH.equals(expectedType)) {
            return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
