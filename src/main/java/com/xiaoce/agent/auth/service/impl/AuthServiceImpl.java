package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.auth.common.utils.GenerateUserInfo;
import com.xiaoce.agent.auth.config.AuthProperties;
import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import com.xiaoce.agent.auth.domain.dto.LoginRequest;
import com.xiaoce.agent.auth.domain.dto.RefreshRequest;
import com.xiaoce.agent.auth.domain.dto.RegisterRequest;
import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.domain.vo.AuthResponse;
import com.xiaoce.agent.auth.domain.vo.TokenPair;
import com.xiaoce.agent.auth.domain.vo.TokenPairResponse;
import com.xiaoce.agent.auth.domain.vo.UserInfoResponse;
import com.xiaoce.agent.auth.enums.GenderEnum;
import com.xiaoce.agent.auth.enums.UserStatus;
import com.xiaoce.agent.auth.mapper.UsersMapper;
import com.xiaoce.agent.auth.service.IAuthService;
import com.xiaoce.agent.auth.service.IRefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.xiaoce.agent.auth.common.constant.RefreshToken.USER_TOKEN_VERSION_KEY_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final IRefreshTokenStore refreshTokenRedisStore;
    private final AuthProperties authProperties;
    private final UsersMapper usersMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    @Override
    public AuthResponse register(RegisterRequest req) {
        if (!req.agreeTerms()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You must agree to the terms and conditions");
        }

        String username = normalizeUsername(req.username());
        String email = normalizeEmail(req.email());

        validateUsername(username);
        validateEmail(email);
        validatePassword(req.password());

        if (usersMapper.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (usersMapper.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        User user = User.builder()
                .academicId(GenerateUserInfo.getAcademicId())
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .gender(GenderEnum.UNKNOWN.getCode())
                .status(UserStatus.ENABLE.getCode())
                .avatarUrl(GenerateUserInfo.getAvatarUrl())
                .bio(GenerateUserInfo.getBio())
                .nickname(GenerateUserInfo.generateUserNickName())
                .school(GenerateUserInfo.getSchool())
                .build();
        usersMapper.insert(user);

        long tokenVersion = getCurrentTokenVersion(user.getId());
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, tokenVersion);
        refreshTokenRedisStore.saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt());

        return new AuthResponse(
                TokenPairResponse.toMapTokenPairResponse(tokenPair),
                UserInfoResponse.toMapUserInfoResponse(user)
        );
    }

    @Transactional(readOnly = true)
    @Override
    public AuthResponse login(LoginRequest req) {
        String identity = req.emailOrUsername() == null ? "" : req.emailOrUsername().trim();
        if (!StringUtils.hasText(identity)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username or email is required");
        }

        User user;
        if (EMAIL_PATTERN.matcher(identity).matches()) {
            user = usersMapper.findByEmail(normalizeEmail(identity));
        } else {
            user = usersMapper.findByUsername(normalizeUsername(identity));
        }
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!StringUtils.hasText(req.password()) || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != null && user.getStatus() == UserStatus.DISABLE.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User is disabled");
        }

        long tokenVersion = getCurrentTokenVersion(user.getId());
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, tokenVersion);
        refreshTokenRedisStore.saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt());

        return new AuthResponse(
                TokenPairResponse.toMapTokenPairResponse(tokenPair),
                UserInfoResponse.toMapUserInfoResponse(user)
        );
    }

    @Transactional
    @Override
    public TokenPairResponse refreshToken(RefreshRequest req) {
        Jwt jwt = jwtTokenService.parseAndVerify(req.refreshToken(), JwtTokenService.TYPE_REFRESH);
        long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
        String jti = jwtTokenService.extractJwtId(jwt);

        boolean validInStore = refreshTokenRedisStore.validateRefreshToken(userId, jti);
        if (!validInStore) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);

        User user = Optional.ofNullable(usersMapper.selectById(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (user.getStatus() != null && user.getStatus() == UserStatus.DISABLE.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User is disabled");
        }

        refreshTokenRedisStore.removeRefreshToken(userId, jti);
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, currentVersion);
        refreshTokenRedisStore.saveRefreshToken(userId, tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt());

        return TokenPairResponse.toMapTokenPairResponse(tokenPair);
    }

    @Transactional(readOnly = true)
    @Override
    public UserInfoResponse me(Long userId) {
        User user = Optional.ofNullable(usersMapper.selectById(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        return UserInfoResponse.toMapUserInfoResponse(user);
    }

    @Transactional
    @Override
    public void logout(String refreshToken) {
        Jwt jwt = jwtTokenService.parseAndVerify(refreshToken, JwtTokenService.TYPE_REFRESH);
        JwtUserInfo jwtUserInfo = jwtTokenService.extractUserInfo(jwt);
        Long userId = jwtUserInfo.userId();
        String jti = jwtTokenService.extractJwtId(jwt);

        boolean isValid = refreshTokenRedisStore.validateRefreshToken(userId, jti);
        if (!isValid) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);
        refreshTokenRedisStore.removeRefreshToken(userId, jti);
    }

    @Transactional
    @Override
    public void logoutAll(String refreshToken) {
        Jwt jwt = jwtTokenService.parseAndVerify(refreshToken, JwtTokenService.TYPE_REFRESH);
        Long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
        String jti = jwtTokenService.extractJwtId(jwt);

        boolean isValid = refreshTokenRedisStore.validateRefreshToken(userId, jti);
        if (!isValid) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);

        refreshTokenRedisStore.removeUserAllRefreshToken(userId);
        bumpTokenVersion(userId);
    }

    @Override
    public void banUserPermanent(Long userId) {
        log.warn("banUserPermanent not implemented, userId={}", userId);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    private void validateEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Email is required");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid email format");
        }
        if (email.length() > 254) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Email is too long");
        }
    }

    private void validateUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username is required");
        }
        if (username.length() < 3 || username.length() > 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username length must be 3-20");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username can only contain letters, digits and underscore");
        }
        if (Character.isDigit(username.charAt(0))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username cannot start with digit");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Password is required");
        }

        String trimmed = password.trim();
        int min = authProperties.getPassword().getMinLength();
        int max = authProperties.getPassword().getMaxLength();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Password length is invalid");
        }

        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Password must contain letters and digits");
        }
    }

    private long getCurrentTokenVersion(Long userId) {
        String raw = redisTemplate.opsForValue().get(USER_TOKEN_VERSION_KEY_PREFIX + userId);
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private void bumpTokenVersion(Long userId) {
        redisTemplate.opsForValue().increment(USER_TOKEN_VERSION_KEY_PREFIX + userId);
    }
}
