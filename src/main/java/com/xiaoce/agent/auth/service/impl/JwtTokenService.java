package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.config.AuthProperties;

import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.domain.vo.TokenPair;
import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_USER_ID = "uid";

    private final AuthProperties authProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    private final Clock clock = Clock.systemUTC();


    public String createAccessToken(String accessTokenId , User user , Instant issueAt, Instant expireAt, String tokenType, List<String> roles){
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(issueAt)
                .expiresAt(expireAt)
                .id( accessTokenId)
                .subject(String.valueOf(user.getId()))
                .claim("nickName", user.getNickname())
                .claim("roles", roles)
                .claim(CLAIM_USER_ID,user.getId())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }


    public String createRefreshToken(String refreshTokenId , User user , Instant issueAt,Instant expireAt,String tokenType,List<String> roles){
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(issueAt)
                .expiresAt(expireAt)
                .subject(String.valueOf(user.getId()))
                .id(refreshTokenId)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .claim("roles", roles)
                .claim(CLAIM_USER_ID,user.getId())

                .build();
                
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

    }

    /**
     * 为用户颁发访问令牌和刷新令牌对
     * @param user 用户对象，包含用户相关信息
     * @return TokenPair 包含访问令牌和刷新令牌的对象
     */
    public TokenPair issueTokenPair(User user){
        // 生成刷新令牌的唯一标识符
        String refreshTokenId = UUID.randomUUID().toString();
        // 生成访问令牌的唯一标识符
        String accessTokenId = UUID.randomUUID().toString();
        // 获取当前时间作为令牌颁发时间
        Instant issueAt = Instant.now(clock);
        // 计算访问令牌的过期时间（基于配置的TTL）
        Instant accessExpiresAt = issueAt.plus(authProperties.getJwt().getAccessTokenTtl());
        // 计算刷新令牌的过期时间（基于配置的TTL）
        Instant refreshExpiresAt = issueAt.plus(authProperties.getJwt().getRefreshTokenTtl());
        // 获取用户的角色列表

        // 生成访问令牌
        String accessToken = createAccessToken(accessTokenId, user, issueAt, accessExpiresAt, TYPE_ACCESS);
        // 生成刷新令牌
        String refreshToken = createRefreshToken(refreshTokenId, user, issueAt, accessExpiresAt, TYPE_REFRESH);
        // 创建并返回包含令牌信息的TokenPair对象
        return new TokenPair(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt,refreshTokenId);
    }

   public Jwt decodeJwt(String token){
        return  jwtDecoder.decode(token);
   }

    /**
     * 解析并验证JWT令牌
     * 
     * @param token JWT令牌
     * @param expectedType 期望的令牌类型
     * @return Jwt 解析后的JWT对象
     */
    public Jwt parseAndVerify(String token, String expectedType) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            
            // 验证令牌类型
            String tokenType = jwt.getClaimAsString(TOKEN_TYPE_CLAIM);
            if (!expectedType.equals(tokenType)) {
                throw invalidTokenError(expectedType);
            }
            
            // 验证发行者
            String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
            if (!authProperties.getJwt().getIssuer().equals(issuer)) {
                throw invalidTokenError(expectedType);
            }
            
            // 验证主题
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                throw invalidTokenError(expectedType);
            }
            
            return jwt;
            
        } catch (Exception e) {
            throw invalidTokenError(expectedType);
        }
    }

    /**
     * 从JWT中提取用户信息
     * 
     * @param jwt JWT对象
     * @return JwtUserInfo 用户信息，包含用户ID、昵称和角色列表
     */
    public JwtUserInfo extractUserInfo(Jwt jwt) {
        parseAndVerify(jwt.getTokenValue(), jwt.getClaim(TOKEN_TYPE_CLAIM));
        String subject = jwt.getSubject();
        String nickName = jwt.getClaimAsString("nickName");
        String username = jwt.getClaimAsString("username");

        Long userId = null;
        try {
            userId = Long.parseLong(subject);
            if (userId == null || userId <= 0) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "userId is inValid");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "extractUserInfo Failed");
        }
        
        // 从JWT中提取角色信息
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            roles = new ArrayList<>();
            roles.add("ROLE_USER");
        }
        
        return new JwtUserInfo(userId, nickName != null ? nickName : username, roles);
    }

    /**
     * 创建访问令牌（兼容新架构）
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param nickname 昵称
     * @param roles 角色列表
     * @return JWT访问令牌
     */
    public String createAccessToken(Long userId, String username, String nickname, List<String> roles) {
        String accessTokenId = UUID.randomUUID().toString();
        Instant issueAt = Instant.now(clock);
        Instant expireAt = issueAt.plus(authProperties.getJwt().getAccessTokenTtl());
        
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .issuedAt(issueAt)
                .expiresAt(expireAt)
                .id(accessTokenId)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("nickName", nickname)
                .claim("roles", roles)
                .claim(CLAIM_USER_ID, userId)
                .claim(TOKEN_TYPE_CLAIM, TYPE_ACCESS)
                .build();
                
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
    public String extractJwtType(Jwt jwt){
        return jwt.getClaimAsString(TOKEN_TYPE_CLAIM);
    }
    public String extractJwtId(Jwt jwt){
        return jwt.getId();
    }

    /**
     * 根据令牌类型创建相应的业务异常
     * 
     * @param expectedType 期望的令牌类型
     * @return BusinessException 业务异常
     */
    private BusinessException invalidTokenError(String expectedType) {
        if (TYPE_REFRESH.equals(expectedType)) {
            return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
