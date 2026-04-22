package com.xiaoce.agent.auth.service.impl;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.xiaoce.agent.auth.config.AuthProperties;
import com.xiaoce.agent.auth.domain.dto.LoginRequest;
import com.xiaoce.agent.auth.domain.dto.RefreshRequest;
import com.xiaoce.agent.auth.domain.dto.RegisterRequest;
import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.service.IAuthService;
import com.xiaoce.agent.auth.domain.vo.AuthResponse;
import com.xiaoce.agent.auth.domain.vo.TokenPairResponse;
import com.xiaoce.agent.auth.domain.vo.UserInfoResponse;
import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenStoreImpl refreshTokenRedisStore;
    private final AuthProperties jwtProperties;

    @Transactional
    @Override
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
        if (req.getPassword() == null || req.getPassword().length() < jwtProperties.getPassword().getMinLength()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Password is too short");
        }

        User user = new User();
        String academicId = UUID.randomUUID().toString().substring(0,8);
        user.setAcademicId(academicId);
        user.setUsername(req.getUsername().trim());
        user.setEmail(req.getEmail().trim().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        userRepository.save(user);
        return new AuthResponse(String.valueOf(user.getId()), "Account created successfully");
    }
    @Override
    public AuthResponse login(LoginRequest req) {
        String identity = req.getEmailOrUsername().trim();
        User user = userRepository.findByUsernameIgnoreCase(identity)
                .or(() -> userRepository.findByEmailIgnoreCase(identity.toLowerCase(Locale.ROOT)))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Account is disabled");
        }
        return issueTokens(user);
    }

    public TokenPairResponse refresh(RefreshRequest req) {
        SignedJWT jwt = jwtTokenService.parseAndVerifyRefresh(req.getRefreshToken());
        try {
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            long userId = Long.parseLong(claims.getSubject());
            String jti = claims.getJWTID();
            refreshTokenRedisStore.validateOwnership(userId, jti);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
            refreshTokenRedisStore.revoke(userId, jti);
            return issueTokens(user);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    @Transactional(readOnly = true)
    public UserInfoResponse me(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return mapUser(user);
    }

    public void logout(long userId) {
        refreshTokenRedisStore.revokeAllForUser(userId);
    }

    private TokenPairResponse issueTokens(User user) {
        String access = jwtTokenService.createAccessToken(user.getId(), user.getUsername());
        String[] refreshPair = jwtTokenService.createRefreshToken(user.getId());
        String refreshJwt = refreshPair[0];
        String jti = refreshPair[1];
        refreshTokenRedisStore.save(user.getId(), jti);
        return new TokenPairResponse(
                access,
                refreshJwt,
                jwtProperties.getJwt().getAccessTokenTtl().toSeconds(),
                jwtProperties.getJwt().getRefreshTokenTtl().toSeconds(),
                "Bearer");
    }

    private UserInfoResponse mapUser(User user) {
        return new UserInfoResponse(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getEmail(),
                user.getUsername(),
                null,
                "USER",
                user.getCreatedAt().atOffset(ZoneOffset.UTC).format(ISO_FORMATTER));
    }
}
