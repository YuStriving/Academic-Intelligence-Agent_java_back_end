package com.xiaoce.agent.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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

/**
 * 认证服务实现类
 * 
 * <p>提供用户认证相关的核心业务逻辑，包括用户注册、登录、登出、获取用户信息和刷新令牌等功能。
 * 该类处理用户数据的持久化、密码加密、JWT令牌管理、Redis刷新令牌存储等核心功能。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>处理用户注册业务，验证用户名和邮箱唯一性</li>
 *   <li>验证用户登录凭据，生成JWT令牌</li>
 *   <li>管理用户会话，刷新和失效令牌</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    /**
     * 邮箱格式正则表达式
     * 用于验证用户输入的邮箱是否符合标准格式
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final IRefreshTokenStore refreshTokenRedisStore;
    private final AuthProperties authProperties;
    private final UsersMapper usersMapper;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 用户注册服务
     * 
     * <p>创建新用户账户，包括验证用户输入、检查用户名和邮箱唯一性、生成用户信息、
     * 加密密码、保存到数据库，并生成JWT令牌对。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户通过注册页面创建新账户</li>
     *   <li>系统管理员创建测试账户</li>
     * </ul>
     * 
     * @param req 注册请求，包含用户名、邮箱、密码和同意条款
     * @return 包含用户信息和JWT令牌对的响应
     */
    @Transactional
    @Override
    public AuthResponse register(RegisterRequest req) {
        // 检查用户是否同意服务条款
        if (!req.agreeTerms()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You must agree to the terms and conditions");
        }

        // 规范化用户名和邮箱格式
        String username = normalizeUsername(req.username());
        String email = normalizeEmail(req.email());

        // 验证输入格式
        validateUsername(username);
        validateEmail(email);
        validatePassword(req.password());

        // 检查用户名和邮箱是否已存在
        if (usersMapper.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (usersMapper.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        // 构建用户对象并自动生成用户信息
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
        log.debug("用户创建成功 - 用户ID: {}, 用户名: {}", user.getId(), username);

        // 生成JWT令牌对并保存刷新令牌到Redis
        long tokenVersion = getCurrentTokenVersion(user.getId());
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, tokenVersion);
        refreshTokenRedisStore.saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt());

        return new AuthResponse(
                TokenPairResponse.toMapTokenPairResponse(tokenPair),
                UserInfoResponse.toMapUserInfoResponse(user)
        );
    }

    /**
     * 用户登录服务
     * 
     * <p>验证用户身份（支持邮箱或用户名登录），检查用户状态，生成JWT令牌对。
     * 如果用户状态被禁用，则拒绝登录。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户通过邮箱和密码登录</li>
     *   <li>用户通过用户名和密码登录</li>
     * </ul>
     * 
     * @param req 登录请求，包含邮箱/用户名和密码
     * @return 包含用户信息和JWT令牌对的响应
     */
    @Transactional(readOnly = true)
    @Override
    public AuthResponse login(LoginRequest req) {
        // 获取并规范化用户身份标识
        String identity = req.emailOrUsername() == null ? "" : req.emailOrUsername().trim();
        if (!StringUtils.hasText(identity)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username or email is required");
        }

        // 根据身份标识格式判断是邮箱还是用户名，并查询用户
        User user;
        if (EMAIL_PATTERN.matcher(identity).matches()) {
            user = usersMapper.findByEmail(normalizeEmail(identity));
        } else {
            user = usersMapper.findByUsername(normalizeUsername(identity));
        }
        
        // 验证用户是否存在
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        
        // 验证密码是否正确
        if (!StringUtils.hasText(req.password()) || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        
        // 检查用户状态是否被禁用
        if (user.getStatus() != null && user.getStatus() == UserStatus.DISABLE.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User is disabled");
        }

        // 生成JWT令牌对并保存刷新令牌到Redis
        long tokenVersion = getCurrentTokenVersion(user.getId());
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, tokenVersion);
        refreshTokenRedisStore.saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt());

        return new AuthResponse(
                TokenPairResponse.toMapTokenPairResponse(tokenPair),
                UserInfoResponse.toMapUserInfoResponse(user)
        );
    }

    /**
     * 刷新访问令牌服务
     * 
     * <p>使用有效的刷新令牌获取新的访问令牌和刷新令牌。原刷新令牌会被失效，
     * 同时验证令牌版本，确保没有被全设备登出。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>访问令牌即将过期，前端自动刷新</li>
     *   <li>用户重新打开应用需要续期</li>
     * </ul>
     * 
     * @param req 刷新令牌请求，包含刷新令牌
     * @return 新的JWT令牌对响应
     */
    @Transactional
    @Override
    public TokenPairResponse refreshToken(RefreshRequest req) {
        // 解析并验证刷新令牌
        Jwt jwt = jwtTokenService.parseAndVerify(req.refreshToken(), JwtTokenService.TYPE_REFRESH);
        long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
        String jti = jwtTokenService.extractJwtId(jwt);

        // 验证刷新令牌是否在Redis中存在且有效
        boolean validInStore = refreshTokenRedisStore.validateRefreshToken(userId, jti);
        if (!validInStore) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 验证令牌版本，防止被全设备登出的令牌继续使用
        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);

        // 查询用户并检查状态
        User user = Optional.ofNullable(usersMapper.selectById(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (user.getStatus() != null && user.getStatus() == UserStatus.DISABLE.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User is disabled");
        }

        // 使旧刷新令牌失效并生成新的令牌对
        refreshTokenRedisStore.removeRefreshToken(userId, jti);
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, currentVersion);
        refreshTokenRedisStore.saveRefreshToken(userId, tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt());

        return TokenPairResponse.toMapTokenPairResponse(tokenPair);
    }

    /**
     * 获取用户信息服务
     * 
     * <p>根据用户ID查询数据库中的用户信息并返回。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户登录后获取个人信息</li>
     *   <li>编辑个人资料前先获取当前信息</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @return 用户详细信息响应
     */
    @Transactional(readOnly = true)
    @Override
    public UserInfoResponse me(Long userId) {
        User user = Optional.ofNullable(usersMapper.selectById(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        return UserInfoResponse.toMapUserInfoResponse(user);
    }

    /**
     * 用户登出服务
     * 
     * <p>使传入的刷新令牌失效，用户需要重新登录才能获取新的令牌。
     * 只影响当前设备的令牌，不影响其他设备。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户主动退出登录</li>
     *   <li>切换账户前先登出</li>
     * </ul>
     * 
     * @param refreshToken 刷新令牌
     */
    @Transactional
    @Override
    public void logout(String refreshToken) {
        // 解析并验证刷新令牌
        Jwt jwt = jwtTokenService.parseAndVerify(refreshToken, JwtTokenService.TYPE_REFRESH);
        JwtUserInfo jwtUserInfo = jwtTokenService.extractUserInfo(jwt);
        Long userId = jwtUserInfo.userId();
        String jti = jwtTokenService.extractJwtId(jwt);

        // 验证刷新令牌有效性
        boolean isValid = refreshTokenRedisStore.validateRefreshToken(userId, jti);
        if (!isValid) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 验证令牌版本并使刷新令牌失效
        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);
        refreshTokenRedisStore.removeRefreshToken(userId, jti);
        log.debug("用户登出成功 - 用户ID: {}, 令牌ID: {}", userId, jti);
    }

    /**
     * 用户全设备登出服务
     * 
     * <p>使该用户所有设备的刷新令牌失效，增加令牌版本号，所有旧令牌都将失效。
     * 用户需要在所有设备上重新登录。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>怀疑账户被盗用，强制所有设备重新登录</li>
     *   <li>修改密码后，希望旧令牌全部失效</li>
     * </ul>
     * 
     * @param refreshToken 刷新令牌
     */
    @Transactional
    @Override
    public void logoutAll(String refreshToken) {
        // 解析并验证刷新令牌
        Jwt jwt = jwtTokenService.parseAndVerify(refreshToken, JwtTokenService.TYPE_REFRESH);
        Long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
        String jti = jwtTokenService.extractJwtId(jwt);

        // 验证刷新令牌有效性
        boolean isValid = refreshTokenRedisStore.validateRefreshToken(userId, jti);
        if (!isValid) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 验证令牌版本，失效该用户所有刷新令牌，并增加令牌版本号
        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);

        refreshTokenRedisStore.removeUserAllRefreshToken(userId);
        bumpTokenVersion(userId);
        log.info("用户全设备登出成功 - 用户ID: {}, 新版本号: {}", userId, currentVersion + 1);
    }

    /**
     * 永久封禁用户
     * 
     * <p>将用户状态设置为禁用，该用户将无法登录系统。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>管理员封禁违规用户</li>
     *   <li>系统自动封禁异常行为用户</li>
     * </ul>
     * 
     * @param userId 用户ID
     */
    @Override
    @Transactional
    public void banUserPermanent(Long userId) {
        if (userId == null){
            throw new BusinessException(ErrorCode.BAD_REQUEST, "User id is required");
        }
        
        // 更新用户状态为禁用
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId);
        wrapper.set("status", UserStatus.DISABLE.getCode());
        int update = usersMapper.update(wrapper);
        if (update == 0){
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        
        log.warn("用户被永久封禁 - 用户ID: {}", userId);
        //TODO 将用户禁用之后，需要让用户进行退出登录操作，后续实现
    }

    /**
     * 规范化邮箱格式
     * 
     * <p>去除首尾空格并转换为小写，保证邮箱存储和查询的一致性。
     * 
     * @param email 原始邮箱
     * @return 规范化后的邮箱
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 规范化用户名格式
     * 
     * <p>去除首尾空格，保持用户名大小写原样。
     * 
     * @param username 原始用户名
     * @return 规范化后的用户名
     */
    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    /**
     * 验证邮箱格式
     * 
     * <p>检查邮箱是否为空、是否符合标准格式、长度是否合理。
     * 
     * @param email 邮箱地址
     */
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

    /**
     * 验证用户名格式
     * 
     * <p>检查用户名是否为空、长度是否在3-20之间、是否只包含字母数字下划线、是否以字母开头。
     * 
     * @param username 用户名
     */
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

    /**
     * 验证密码格式
     * 
     * <p>检查密码是否为空、长度是否符合配置要求、是否同时包含字母和数字。
     * 
     * @param password 密码
     */
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

    /**
     * 获取用户当前令牌版本号
     * 
     * <p>从Redis中查询用户的令牌版本号，如果不存在则返回0。
     * 令牌版本号用于实现全设备登出功能。
     * 
     * @param userId 用户ID
     * @return 当前令牌版本号
     */
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

    /**
     * 增加用户令牌版本号
     * 
     * <p>在Redis中递增用户的令牌版本号，这样所有旧令牌都会失效，
     * 用户需要重新登录才能获取新的令牌。
     * 
     * @param userId 用户ID
     */
    private void bumpTokenVersion(Long userId) {
        redisTemplate.opsForValue().increment(USER_TOKEN_VERSION_KEY_PREFIX + userId);
    }
}
