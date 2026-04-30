package com.xiaoce.agent.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.auth.common.utils.GenerateUserInfo;
import com.xiaoce.agent.auth.config.AuthProperties;
import com.xiaoce.agent.auth.domain.dto.*;
import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.domain.vo.*;
import com.xiaoce.agent.auth.enums.ClientType;
import com.xiaoce.agent.auth.enums.GenderEnum;
import com.xiaoce.agent.auth.enums.UserIsDeleted;
import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;
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
import java.util.Optional;

import static com.xiaoce.agent.auth.common.constant.AuthConstant.EMAIL_PATTERN;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.USER_IS_BANNED_BITMAP_KEY;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.USER_TOKEN_VERSION_KEY_PREFIX;
import static com.xiaoce.agent.auth.common.utils.UserInfoUtils.*;

/**
 * 认证服务核心实现
 *
 * <p>职责：处理用户注册、登录、令牌管理、账号安全等所有认证相关业务。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>JWT双令牌机制</b>：AccessToken(短期) + RefreshToken(长期)，支持无感知续期</li>
 *   <li><b>令牌版本号</b>：通过Redis递增版本号实现全设备登出</li>
 *   <li><b>多客户端支持</b>：Web/App等不同客户端类型可独立管理令牌</li>
 *   <li><b>安全防护</b>：用户封禁使用Bitmap存储，密码BCrypt加密，验证码防刷</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final IRefreshTokenStore refreshTokenRedisStore;
    private final AuthProperties authProperties;
    private final UsersMapper usersMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ValidateCodeImpl validateCode;

    /**
     * 用户注册
     *
     * <p>流程：输入校验 → 唯一性检查 → 验证码验证 → 创建用户 → 签发令牌
     *
     * @param req 注册请求（用户名、邮箱、密码、验证码）
     * @param clientType 客户端类型（Web/App）
     * @return 用户信息 + JWT令牌对
     */
    @Transactional
    @Override
    public AuthResponse register(RegisterRequest req, ClientType clientType) {
        // 1. 基础校验
        if (!req.agreeTerms()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先同意服务条款");
        }

        String username = normalizeUsername(req.username());
        String email = normalizeEmail(req.email());

        // 2. 格式校验
        validateUsername(username);
        validateEmail(email);
        validatePassword(req.password());

        // 3. 唯一性校验（防止重复注册）
        if (usersMapper.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (usersMapper.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        // 4. 验证码校验（防止恶意注册）
        ValidateCodeResult codeResult = validateCode.verifyCode(email, ValidateCodeSendSceneEnums.REGISTER, req.validateCode());
        if (!codeResult.isSuccess()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误");
        }

        // 5. 创建用户并持久化
        User user = User.builder()
                .academicId(GenerateUserInfo.getAcademicId())
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .gender(GenderEnum.UNKNOWN.getCode())
                .avatarUrl(GenerateUserInfo.getAvatarUrl())
                .bio(GenerateUserInfo.getBio())
                .nickname(GenerateUserInfo.generateUserNickName())
                .school(GenerateUserInfo.getSchool())
                .isDeleted(UserIsDeleted.NO.getCode())
                .build();
        usersMapper.insert(user);

        // 6. 签发JWT令牌对
        long tokenVersion = getCurrentTokenVersion(user.getId());
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, tokenVersion);

        // 预清理：主动清理该客户端类型的超额令牌（只保留最新的1个），防止令牌堆积
        refreshTokenRedisStore.cleanupExcessTokens(user.getId(), clientType, 1);

        refreshTokenRedisStore.saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt(), clientType);

        return new AuthResponse(TokenPairResponse.toMapTokenPairResponse(tokenPair), UserInfoResponse.toMapUserInfoResponse(user));
    }

    /**
     * 用户登录
     *
     * <p>支持邮箱或用户名登录。为防止枚举攻击，用户不存在和密码错误返回相同错误码。
     *
     * @param req 登录凭证（邮箱/用户名 + 密码）
     * @param clientType 客户端类型
     * @return 用户信息 + JWT令牌对
     */
    @Transactional(readOnly = true)
    @Override
    public AuthResponse login(LoginRequest req, ClientType clientType) {
        String identity = req.emailOrUsername() == null ? "" : req.emailOrUsername().trim();
        if (!StringUtils.hasText(identity)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入邮箱或用户名");
        }

        // 根据输入格式自动识别查询方式
        User user = EMAIL_PATTERN.matcher(identity).matches()
                ? usersMapper.findByEmail(normalizeEmail(identity))
                : usersMapper.findByUsername(normalizeUsername(identity));

        // 安全：统一返回"凭证无效"，不泄露用户是否存在
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 封禁检查
        if (isUserBannedByBitmap(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被封禁");
        }
        if (user.getIsDeleted().equals(UserIsDeleted.YES.getCode())){
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被删除");
        }

        // 签发新令牌（同一客户端类型的旧令牌会在saveRefreshToken的Lua脚本中自动清理）
        long tokenVersion = getCurrentTokenVersion(user.getId());
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, tokenVersion);

        // 预清理：主动清理该客户端类型的超额令牌（只保留最新的1个），防止令牌堆积
        refreshTokenRedisStore.cleanupExcessTokens(user.getId(), clientType, 1);

        refreshTokenRedisStore.saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt(), clientType);

        return new AuthResponse(TokenPairResponse.toMapTokenPairResponse(tokenPair), UserInfoResponse.toMapUserInfoResponse(user));
    }

    /**
     * 刷新访问令牌
     *
     * <p>当AccessToken过期时调用。采用"先创建后删除"策略保证原子性：
     * 即使删除旧RT失败，用户仍可用新RT继续访问。
     *
     * @param req 包含RefreshToken的刷新请求
     * @param clientType 客户端类型
     * @return 新的JWT令牌对
     */
    @Transactional
    @Override
    public TokenPairResponse refreshToken(RefreshRequest req, ClientType clientType) {
        // 1. 解析并验证RefreshToken签名和有效期
        Jwt jwt = jwtTokenService.parseAndVerify(req.refreshToken(), JwtTokenService.TYPE_REFRESH);
        long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
        String jti = jwtTokenService.extractJwtId(jwt);

        // 2. 验证RT是否在Redis中存在且未被使用
        if (!refreshTokenRedisStore.validateRefreshToken(userId, jti, clientType)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 3. 版本号校验（防止被全设备登出的旧RT被滥用）
        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);

        // 4. 用户状态检查
        User user = Optional.ofNullable(usersMapper.selectById(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (isUserBannedByBitmap(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被封禁");
        }

        // 5. 先生成新令牌对（原子性保障）
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, currentVersion);
        refreshTokenRedisStore.saveRefreshToken(userId, tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt(), clientType);

        // 6. 最后失效旧RT（即使失败也不影响新RT的使用）
        refreshTokenRedisStore.removeRefreshToken(userId, jti, clientType);

        return TokenPairResponse.toMapTokenPairResponse(tokenPair);
    }

    /**
     * 获取当前用户信息
     *
     * @param userId 从JWT中解析的用户ID
     * @return 用户详细信息
     */
    @Transactional(readOnly = true)
    @Override
    public UserInfoResponse me(Long userId) {
        User user = Optional.ofNullable(usersMapper.selectById(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        return UserInfoResponse.toMapUserInfoResponse(user);
    }

    /**
     * 单设备登出
     *
     * <p>仅使当前设备的RefreshToken失效，其他设备不受影响。
     * 注意：由于JWT无状态特性，AccessToken在过期前仍有效。
     *
     * @param refreshToken 当前设备的RefreshToken
     * @param clientType 客户端类型
     */
    @Transactional
    @Override
    public void logout(String refreshToken, ClientType clientType) {
        Jwt jwt = jwtTokenService.parseAndVerify(refreshToken, JwtTokenService.TYPE_REFRESH);
        JwtUserInfo jwtUserInfo = jwtTokenService.extractUserInfo(jwt);
        Long userId = jwtUserInfo.userId();
        String jti = jwtTokenService.extractJwtId(jwt);

        if (!refreshTokenRedisStore.validateRefreshToken(userId, jti, clientType)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "令牌无效或已过期");
        }

        // 版本号校验
        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);

        refreshTokenRedisStore.removeRefreshToken(userId, jti, clientType);
        log.debug("单设备登出成功 - 用户ID: {}, JTI: {}", userId, jti);
    }

    /**
     * 全设备登出
     *
     * <p>通过递增令牌版本号 + 清除所有RT实现：
     * - 版本号+1后，所有旧版本的JWT（包括未过期的AccessToken）都会在校验时被拒绝
     * - 同时清除Redis中的所有RT，加速失效过程
     *
     * @param refreshToken 用于身份验证的当前RT
     * @param clientType 客户端类型
     */
    @Transactional
    @Override
    public void logoutAll(String refreshToken, ClientType clientType) {
        Jwt jwt = jwtTokenService.parseAndVerify(refreshToken, JwtTokenService.TYPE_REFRESH);
        Long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
        String jti = jwtTokenService.extractJwtId(jwt);

        if (!refreshTokenRedisStore.validateRefreshToken(userId, jti, clientType)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "令牌无效或已过期");
        }

        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);

        // 关键：先递增版本号，再清除RT（顺序不能反）
        bumpTokenVersion(userId);
        refreshTokenRedisStore.removeUserAllRefreshToken(userId, clientType);
        log.info("全设备登出成功 - 用户ID: {}, 新版本: {}", userId, currentVersion + 1);
    }

    /**
     * 永久封禁用户
     *
     * <p>效果：无法登录 + 所有令牌失效 + RT全部清除
     * 封禁状态存储在Redis Bitmap中，重启不丢失。
     *
     * @param userId 目标用户ID
     * @param clientType 客户端类型
     */
    @Override
    @Transactional
    public void banUserPermanent(Long userId, ClientType clientType) {
        if (userId == null || userId < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户ID无效");
        }

        if (usersMapper.selectById(userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 三管齐下：标记封禁 + 版本号+1（使旧令牌失效） + 清除RT
        markUserBannedInBitmap(userId);
        bumpTokenVersion(userId);
        refreshTokenRedisStore.removeUserAllRefreshToken(userId, clientType);
        log.info("用户封禁成功 - 用户ID: {}", userId);
    }

    /**
     * 已登录用户重置密码
     *
     * <p>需要提供旧密码 + 验证码 + 新密码。重置后强制全设备登出。
     *
     * @param request 重置密码请求
     * @param clientType 客户端类型
     */
    @Override
    @Transactional
    public void resetPassword(PasswordResetRequest request, ClientType clientType) {
        String identity = request.emailOrUsername() == null ? "" : request.emailOrUsername().trim();
        if (!StringUtils.hasText(identity)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入邮箱或用户名");
        }

        User user = EMAIL_PATTERN.matcher(identity).matches()
                ? usersMapper.findByEmail(normalizeEmail(identity))
                : usersMapper.findByUsername(normalizeUsername(identity));

        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 旧密码校验
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 新密码格式校验
        validatePassword(request.newPassword());

        // 防止设置与旧密码相同的新密码
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与旧密码相同");
        }

        // 验证码校验
        ValidateCodeResult codeResult = validateCode.verifyCode(user.getEmail(), ValidateCodeSendSceneEnums.RESETPASSWORD, request.code());
        if (!codeResult.isSuccess()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误");
        }

        // 更新密码
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", user.getId());
        wrapper.set("password_hash", passwordEncoder.encode(request.newPassword()));
        if (usersMapper.update(wrapper) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "更新失败");
        }

        // 安全措施：重置密码后强制全设备重新登录
        bumpTokenVersion(user.getId());
        refreshTokenRedisStore.removeUserAllRefreshToken(user.getId(), clientType);
        log.info("密码重置成功 - 用户ID: {}", user.getId());
    }

    /**
     * 发送验证码
     *
     * <p>根据不同场景决定发送目标：
     * - 注册：发送到输入的新邮箱
     * - 修改邮箱/学术ID：发送到数据库中的当前邮箱（证明身份所有权）
     * - 忘记/重置密码：发送到数据库中的当前邮箱
     *
     * @param request 包含标识和场景的请求
     * @return 发送结果
     */
    @Override
    public SendCodeResponse sendValidateCode(SendCodeRequest request) {
        String emailOrUsername = request.emailOrUsername();
        ValidateCodeSendSceneEnums scene = request.scene();

        if (scene == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少场景参数");
        }

        // 规范化输入
        String identify = EMAIL_PATTERN.matcher(emailOrUsername).matches()
                ? normalizeEmail(emailOrUsername)
                : normalizeUsername(emailOrUsername);

        if (identify == null || !StringUtils.hasText(identify)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱或用户名不能为空");
        }

        // 查找用户
        User user = EMAIL_PATTERN.matcher(identify).matches()
                ? usersMapper.findByEmail(identify)
                : usersMapper.findByUsername(identify);

        // 场景化路由：确定验证码发送的目标邮箱
        String email;
        switch (scene) {
            case REGISTER:
                if (user != null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "该账户已存在");
                }
                email = identify;  // 注册场景用新邮箱
                break;
            case RESETPASSWORD:
            case FORGETPASSWORD:
                if (user == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在");
                }
                email = user.getEmail();  // 用数据库中的邮箱
                break;
            case UPDATEUSEREMAIL:
            case UPDATEUSERACADEMICID:
                if (user == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "请先登录");
                }
                email = user.getEmail();  // 用当前邮箱验证身份
                break;
            case DELETEACCOUNT:
                if (user == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在");
                }
                email = user.getEmail();  // 删除账号需验证身份，发送到注册邮箱
                break;
            default:
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的场景: " + scene);
        }

        return validateCode.sendValidateCode(email, scene);
    }

    /**
     * 忘记密码（未登录状态）
     *
     * <p>通过验证码 + 新密码重置，无需旧密码。重置后强制全设备登出。
     *
     * @param request 包含标识、验证码和新密码的请求
     * @param clientType 客户端类型
     */
    @Override
    public void forgetPassword(ForgetPasswordRequest request, ClientType clientType) {
        User user = EMAIL_PATTERN.matcher(request.emailOrUsername()).matches()
                ? usersMapper.findByEmail(normalizeEmail(request.emailOrUsername()))
                : usersMapper.findByUsername(normalizeUsername(request.emailOrUsername()));

        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 验证码校验
        ValidateCodeResult result = validateCode.verifyCode(user.getEmail(), ValidateCodeSendSceneEnums.FORGETPASSWORD, request.code());
        if (!result.isSuccess()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误");
        }

        // 更新密码
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", user.getId());
        wrapper.set("password_hash", passwordEncoder.encode(request.newPassword()));
        if (usersMapper.update(wrapper) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "更新失败");
        }

        // 强制全设备重新登录
        bumpTokenVersion(user.getId());
        refreshTokenRedisStore.removeUserAllRefreshToken(user.getId(), clientType);
        log.info("忘记密码重置成功 - 用户ID: {}", user.getId());
    }

    // ==================== 私有工具方法 ====================

    /**
     * 密码强度校验
     *
     * <p>规则：非空 + 长度限制（配置） + 必须包含字母和数字
     */
    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能为空");
        }

        String trimmed = password.trim();
        int min = authProperties.getPassword().getMinLength();
        int max = authProperties.getPassword().getMaxLength();

        if (trimmed.length() < min || trimmed.length() > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    String.format("密码长度需在%d-%d位之间", min, max));
        }

        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);

        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码必须同时包含字母和数字");
        }
    }

    /**
     * 获取当前令牌版本号
     *
     * <p>用于全设备登出机制：每次logoutAll时版本号+1，
     * 所有携带旧版本号的JWT都会在校验时被拒绝。
     *
     * @return 当前版本号（默认0）
     */
    private long getCurrentTokenVersion(Long userId) {
        String raw = redisTemplate.opsForValue().get(USER_TOKEN_VERSION_KEY_PREFIX + userId);
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;  // 数据异常时降级为0
        }
    }

    /**
     * 递增令牌版本号（触发全设备登出）
     */
    private void bumpTokenVersion(Long userId) {
        redisTemplate.opsForValue().increment(USER_TOKEN_VERSION_KEY_PREFIX + userId);
    }

    /**
     * 在Redis Bitmap中标记用户为已封禁
     *
     * <p>选择Bitmap的原因：内存效率高（1个用户仅占1bit），
     * 适合存储大量用户的封禁状态。
     */
    private void markUserBannedInBitmap(Long userId) {
        redisTemplate.opsForValue().setBit(USER_IS_BANNED_BITMAP_KEY, userId, true);
    }

    /**
     * 检查用户是否被封禁
     *
     * @return true=已封禁（拒绝登录/刷新），false=正常
     */
    private boolean isUserBannedByBitmap(Long userId) {
        Boolean banned = redisTemplate.opsForValue().getBit(USER_IS_BANNED_BITMAP_KEY, userId);
        return Boolean.TRUE.equals(banned);
    }
}
