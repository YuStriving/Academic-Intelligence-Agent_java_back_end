package com.xiaoce.agent.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.auth.common.utils.GenerateUserInfo;
import com.xiaoce.agent.auth.config.AuthProperties;
import com.xiaoce.agent.auth.domain.dto.*;
import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.domain.vo.AuthResponse;
import com.xiaoce.agent.auth.domain.vo.TokenPair;
import com.xiaoce.agent.auth.domain.vo.TokenPairResponse;
import com.xiaoce.agent.auth.domain.vo.UserInfoResponse;
import com.xiaoce.agent.auth.enums.GenderEnum;
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

import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.USER_IS_BANNED_BITMAP_KEY;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.USER_TOKEN_VERSION_KEY_PREFIX;

/**
 * 璁よ瘉鏈嶅姟瀹炵幇绫?
 * 
 * <p>鎻愪緵鐢ㄦ埛璁よ瘉鐩稿叧鐨勬牳蹇冧笟鍔￠€昏緫锛屽寘鎷敤鎴锋敞鍐屻€佺櫥褰曘€佺櫥鍑恒€佽幏鍙栫敤鎴蜂俊鎭拰鍒锋柊浠ょ墝绛夊姛鑳姐€?
 * 璇ョ被澶勭悊鐢ㄦ埛鏁版嵁鐨勬寔涔呭寲銆佸瘑鐮佸姞瀵嗐€丣WT浠ょ墝绠＄悊銆丷edis鍒锋柊浠ょ墝瀛樺偍绛夋牳蹇冨姛鑳姐€?
 * 
 * <p>浣跨敤鍦烘櫙锛?
 * <ul>
 *   <li>澶勭悊鐢ㄦ埛娉ㄥ唽涓氬姟锛岄獙璇佺敤鎴峰悕鍜岄偖绠卞敮涓€鎬?/li>
 *   <li>楠岃瘉鐢ㄦ埛鐧诲綍鍑嵁锛岀敓鎴怞WT浠ょ墝</li>
 *   <li>绠＄悊鐢ㄦ埛浼氳瘽锛屽埛鏂板拰澶辨晥浠ょ墝</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    /**
     * 閭鏍煎紡姝ｅ垯琛ㄨ揪寮?
     * 鐢ㄤ簬楠岃瘉鐢ㄦ埛杈撳叆鐨勯偖绠辨槸鍚︾鍚堟爣鍑嗘牸寮?
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final IRefreshTokenStore refreshTokenRedisStore;
    private final AuthProperties authProperties;
    private final UsersMapper usersMapper;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 鐢ㄦ埛娉ㄥ唽鏈嶅姟
     * 
     * <p>鍒涘缓鏂扮敤鎴疯处鎴凤紝鍖呮嫭楠岃瘉鐢ㄦ埛杈撳叆銆佹鏌ョ敤鎴峰悕鍜岄偖绠卞敮涓€鎬с€佺敓鎴愮敤鎴蜂俊鎭€?
     * 鍔犲瘑瀵嗙爜銆佷繚瀛樺埌鏁版嵁搴擄紝骞剁敓鎴怞WT浠ょ墝瀵广€?
     * 
     * <p>浣跨敤鍦烘櫙锛?
     * <ul>
     *   <li>鐢ㄦ埛閫氳繃娉ㄥ唽椤甸潰鍒涘缓鏂拌处鎴?/li>
     *   <li>绯荤粺绠＄悊鍛樺垱寤烘祴璇曡处鎴?/li>
     * </ul>
     * 
     * @param req 娉ㄥ唽璇锋眰锛屽寘鍚敤鎴峰悕銆侀偖绠便€佸瘑鐮佸拰鍚屾剰鏉℃
     * @return 鍖呭惈鐢ㄦ埛淇℃伅鍜孞WT浠ょ墝瀵圭殑鍝嶅簲
     */
    @Transactional
    @Override
    public AuthResponse register(RegisterRequest req) {
        // 妫€鏌ョ敤鎴锋槸鍚﹀悓鎰忔湇鍔℃潯娆?
        if (!req.agreeTerms()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "You must agree to the terms and conditions");
        }

        // 瑙勮寖鍖栫敤鎴峰悕鍜岄偖绠辨牸寮?
        String username = normalizeUsername(req.username());
        String email = normalizeEmail(req.email());

        // 楠岃瘉杈撳叆鏍煎紡
        validateUsername(username);
        validateEmail(email);
        validatePassword(req.password());

        // 妫€鏌ョ敤鎴峰悕鍜岄偖绠辨槸鍚﹀凡瀛樺湪
        if (usersMapper.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (usersMapper.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        // 鏋勫缓鐢ㄦ埛瀵硅薄骞惰嚜鍔ㄧ敓鎴愮敤鎴蜂俊鎭?
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
                .build();
        usersMapper.insert(user);
        log.debug("鐢ㄦ埛鍒涘缓鎴愬姛 - 鐢ㄦ埛ID: {}, 鐢ㄦ埛鍚? {}", user.getId(), username);

        // 鐢熸垚JWT浠ょ墝瀵瑰苟淇濆瓨鍒锋柊浠ょ墝鍒癛edis
        long tokenVersion = getCurrentTokenVersion(user.getId());
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, tokenVersion);
        refreshTokenRedisStore.saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt());

        return new AuthResponse(
                TokenPairResponse.toMapTokenPairResponse(tokenPair),
                UserInfoResponse.toMapUserInfoResponse(user)
        );
    }

    /**
     * 鐢ㄦ埛鐧诲綍鏈嶅姟
     * 
     * <p>楠岃瘉鐢ㄦ埛韬唤锛堟敮鎸侀偖绠辨垨鐢ㄦ埛鍚嶇櫥褰曪級锛屾鏌ョ敤鎴风姸鎬侊紝鐢熸垚JWT浠ょ墝瀵广€?
     * 濡傛灉鐢ㄦ埛鐘舵€佽绂佺敤锛屽垯鎷掔粷鐧诲綍銆?
     * 
     * <p>浣跨敤鍦烘櫙锛?
     * <ul>
     *   <li>鐢ㄦ埛閫氳繃閭鍜屽瘑鐮佺櫥褰?/li>
     *   <li>鐢ㄦ埛閫氳繃鐢ㄦ埛鍚嶅拰瀵嗙爜鐧诲綍</li>
     * </ul>
     * 
     * @param req 鐧诲綍璇锋眰锛屽寘鍚偖绠?鐢ㄦ埛鍚嶅拰瀵嗙爜
     * @return 鍖呭惈鐢ㄦ埛淇℃伅鍜孞WT浠ょ墝瀵圭殑鍝嶅簲
     */
    @Transactional(readOnly = true)
    @Override
    public AuthResponse login(LoginRequest req) {
        // 鑾峰彇骞惰鑼冨寲鐢ㄦ埛韬唤鏍囪瘑
        String identity = req.emailOrUsername() == null ? "" : req.emailOrUsername().trim();
        if (!StringUtils.hasText(identity)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username or email is required");
        }

        // 鏍规嵁韬唤鏍囪瘑鏍煎紡鍒ゆ柇鏄偖绠辫繕鏄敤鎴峰悕锛屽苟鏌ヨ鐢ㄦ埛
        User user;
        if (EMAIL_PATTERN.matcher(identity).matches()) {
            user = usersMapper.findByEmail(normalizeEmail(identity));
        } else {
            user = usersMapper.findByUsername(normalizeUsername(identity));
        }
        
        // 楠岃瘉鐢ㄦ埛鏄惁瀛樺湪
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        
        // 楠岃瘉瀵嗙爜鏄惁姝ｇ‘
        if (!StringUtils.hasText(req.password()) || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (isUserBannedByBitmap(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User has been banned");
        }


        // 鐢熸垚JWT浠ょ墝瀵瑰苟淇濆瓨鍒锋柊浠ょ墝鍒癛edis
        long tokenVersion = getCurrentTokenVersion(user.getId());
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, tokenVersion);
        refreshTokenRedisStore.saveRefreshToken(user.getId(), tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt());

        return new AuthResponse(
                TokenPairResponse.toMapTokenPairResponse(tokenPair),
                UserInfoResponse.toMapUserInfoResponse(user)
        );
    }

    /**
     * 鍒锋柊璁块棶浠ょ墝鏈嶅姟
     * 
     * <p>浣跨敤鏈夋晥鐨勫埛鏂颁护鐗岃幏鍙栨柊鐨勮闂护鐗屽拰鍒锋柊浠ょ墝銆傚師鍒锋柊浠ょ墝浼氳澶辨晥锛?
     * 鍚屾椂楠岃瘉浠ょ墝鐗堟湰锛岀‘淇濇病鏈夎鍏ㄨ澶囩櫥鍑恒€?
     * 
     * <p>浣跨敤鍦烘櫙锛?
     * <ul>
     *   <li>璁块棶浠ょ墝鍗冲皢杩囨湡锛屽墠绔嚜鍔ㄥ埛鏂?/li>
     *   <li>鐢ㄦ埛閲嶆柊鎵撳紑搴旂敤闇€瑕佺画鏈?/li>
     * </ul>
     * 
     * @param req 鍒锋柊浠ょ墝璇锋眰锛屽寘鍚埛鏂颁护鐗?
     * @return 鏂扮殑JWT浠ょ墝瀵瑰搷搴?
     */
    @Transactional
    @Override
    public TokenPairResponse refreshToken(RefreshRequest req) {
        // 瑙ｆ瀽骞堕獙璇佸埛鏂颁护鐗?
        Jwt jwt = jwtTokenService.parseAndVerify(req.refreshToken(), JwtTokenService.TYPE_REFRESH);
        long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
        String jti = jwtTokenService.extractJwtId(jwt);

        // 楠岃瘉鍒锋柊浠ょ墝鏄惁鍦≧edis涓瓨鍦ㄤ笖鏈夋晥
        boolean validInStore = refreshTokenRedisStore.validateRefreshToken(userId, jti);
        if (!validInStore) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 楠岃瘉浠ょ墝鐗堟湰锛岄槻姝㈣鍏ㄨ澶囩櫥鍑虹殑浠ょ墝缁х画浣跨敤
        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);

        // 鏌ヨ鐢ㄦ埛骞舵鏌ョ姸鎬?
        User user = Optional.ofNullable(usersMapper.selectById(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (isUserBannedByBitmap(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "User has been banned");
        }


        // 浣挎棫鍒锋柊浠ょ墝澶辨晥骞剁敓鎴愭柊鐨勪护鐗屽
        refreshTokenRedisStore.removeRefreshToken(userId, jti);
        TokenPair tokenPair = jwtTokenService.issueTokenPair(user, currentVersion);
        refreshTokenRedisStore.saveRefreshToken(userId, tokenPair.refreshTokenId(), tokenPair.refreshTokenExpiresAt());

        return TokenPairResponse.toMapTokenPairResponse(tokenPair);
    }

    /**
     * 鑾峰彇鐢ㄦ埛淇℃伅鏈嶅姟
     * 
     * <p>鏍规嵁鐢ㄦ埛ID鏌ヨ鏁版嵁搴撲腑鐨勭敤鎴蜂俊鎭苟杩斿洖銆?
     * 
     * <p>浣跨敤鍦烘櫙锛?
     * <ul>
     *   <li>鐢ㄦ埛鐧诲綍鍚庤幏鍙栦釜浜轰俊鎭?/li>
     *   <li>缂栬緫涓汉璧勬枡鍓嶅厛鑾峰彇褰撳墠淇℃伅</li>
     * </ul>
     * 
     * @param userId 鐢ㄦ埛ID
     * @return 鐢ㄦ埛璇︾粏淇℃伅鍝嶅簲
     */
    @Transactional(readOnly = true)
    @Override
    public UserInfoResponse me(Long userId) {
        User user = Optional.ofNullable(usersMapper.selectById(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        return UserInfoResponse.toMapUserInfoResponse(user);
    }

    /**
     * 鐢ㄦ埛鐧诲嚭鏈嶅姟
     * 
     * <p>浣夸紶鍏ョ殑鍒锋柊浠ょ墝澶辨晥锛岀敤鎴烽渶瑕侀噸鏂扮櫥褰曟墠鑳借幏鍙栨柊鐨勪护鐗屻€?
     * 鍙奖鍝嶅綋鍓嶈澶囩殑浠ょ墝锛屼笉褰卞搷鍏朵粬璁惧銆?
     * 
     * <p>浣跨敤鍦烘櫙锛?
     * <ul>
     *   <li>鐢ㄦ埛涓诲姩閫€鍑虹櫥褰?/li>
     *   <li>鍒囨崲璐︽埛鍓嶅厛鐧诲嚭</li>
     * </ul>
     * 
     * @param refreshToken 鍒锋柊浠ょ墝
     */
    @Transactional
    @Override
    public void logout(String refreshToken) {
        // 瑙ｆ瀽骞堕獙璇佸埛鏂颁护鐗?
        Jwt jwt = jwtTokenService.parseAndVerify(refreshToken, JwtTokenService.TYPE_REFRESH);
        JwtUserInfo jwtUserInfo = jwtTokenService.extractUserInfo(jwt);
        Long userId = jwtUserInfo.userId();
        String jti = jwtTokenService.extractJwtId(jwt);

        // 楠岃瘉鍒锋柊浠ょ墝鏈夋晥鎬?
        boolean isValid = refreshTokenRedisStore.validateRefreshToken(userId, jti);
        if (!isValid) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 楠岃瘉浠ょ墝鐗堟湰骞朵娇鍒锋柊浠ょ墝澶辨晥
        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);
        refreshTokenRedisStore.removeRefreshToken(userId, jti);
        log.debug("鐢ㄦ埛鐧诲嚭鎴愬姛 - 鐢ㄦ埛ID: {}, 浠ょ墝ID: {}", userId, jti);
    }

    /**
     * 鐢ㄦ埛鍏ㄨ澶囩櫥鍑烘湇鍔?
     * 
     * <p>浣胯鐢ㄦ埛鎵€鏈夎澶囩殑鍒锋柊浠ょ墝澶辨晥锛屽鍔犱护鐗岀増鏈彿锛屾墍鏈夋棫浠ょ墝閮藉皢澶辨晥銆?
     * 鐢ㄦ埛闇€瑕佸湪鎵€鏈夎澶囦笂閲嶆柊鐧诲綍銆?
     * 
     * <p>浣跨敤鍦烘櫙锛?
     * <ul>
     *   <li>鎬€鐤戣处鎴疯鐩楃敤锛屽己鍒舵墍鏈夎澶囬噸鏂扮櫥褰?/li>
     *   <li>淇敼瀵嗙爜鍚庯紝甯屾湜鏃т护鐗屽叏閮ㄥけ鏁?/li>
     * </ul>
     * 
     * @param refreshToken 鍒锋柊浠ょ墝
     */
    @Transactional
    @Override
    public void logoutAll(String refreshToken) {
        // 瑙ｆ瀽骞堕獙璇佸埛鏂颁护鐗?
        Jwt jwt = jwtTokenService.parseAndVerify(refreshToken, JwtTokenService.TYPE_REFRESH);
        Long userId = jwtTokenService.parseSubjectAsUserId(jwt.getSubject());
        String jti = jwtTokenService.extractJwtId(jwt);

        // 楠岃瘉鍒锋柊浠ょ墝鏈夋晥鎬?
        boolean isValid = refreshTokenRedisStore.validateRefreshToken(userId, jti);
        if (!isValid) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 楠岃瘉浠ょ墝鐗堟湰锛屽け鏁堣鐢ㄦ埛鎵€鏈夊埛鏂颁护鐗岋紝骞跺鍔犱护鐗岀増鏈彿
        long currentVersion = getCurrentTokenVersion(userId);
        jwtTokenService.assertTokenVersion(jwt, currentVersion, JwtTokenService.TYPE_REFRESH);

        bumpTokenVersion(userId);
        refreshTokenRedisStore.removeUserAllRefreshToken(userId);
        log.info("鐢ㄦ埛鍏ㄨ澶囩櫥鍑烘垚鍔?- 鐢ㄦ埛ID: {}, 鏂扮増鏈彿: {}", userId, currentVersion + 1);
    }

    /**
     * 姘镐箙灏佺鐢ㄦ埛
     * 
     * <p>灏嗙敤鎴风姸鎬佽缃负绂佺敤锛岃鐢ㄦ埛灏嗘棤娉曠櫥褰曠郴缁熴€?
     * 
     * <p>浣跨敤鍦烘櫙锛?
     * <ul>
     *   <li>绠＄悊鍛樺皝绂佽繚瑙勭敤鎴?/li>
     *   <li>绯荤粺鑷姩灏佺寮傚父琛屼负鐢ㄦ埛</li>
     * </ul>
     * 
     * @param userId 鐢ㄦ埛ID
     */
    @Override
    @Transactional
    public void banUserPermanent(Long userId) {
        if (userId == null || userId < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        User user = usersMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }
        markUserBannedInBitmap(userId);
        bumpTokenVersion(userId);
        refreshTokenRedisStore.removeUserAllRefreshToken(userId);
        log.info("鐢ㄦ埛姘镐箙灏佺鎴愬姛 - 鐢ㄦ埛ID: {}", userId);
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        // 鑾峰彇骞惰鑼冨寲鐢ㄦ埛韬唤鏍囪瘑
        String identity = request.emailOrUsername() == null ? "" : request.emailOrUsername().trim();
        if (!StringUtils.hasText(identity)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username or email is required");
        }

        // 楠岃瘉鐮佸熀纭€鏍煎紡鏍￠獙锛堥暱搴︾敱閰嶇疆椹卞姩锛?
        String code = request.code() == null ? "" : request.code().trim();
        int codeLength = authProperties.getVerification().getCodeLength();
        if (!StringUtils.hasText(code) || code.length() != codeLength || !code.chars().allMatch(Character::isDigit)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid verification code");
        }

        // 閫氳繃閭鎴栫敤鎴峰悕鏌ユ壘鐢ㄦ埛
        User user;
        if (EMAIL_PATTERN.matcher(identity).matches()) {
            user = usersMapper.findByEmail(normalizeEmail(identity));
        } else {
            user = usersMapper.findByUsername(normalizeUsername(identity));
        }

        // 鏍￠獙鐢ㄦ埛鐘舵€佸拰鏃у瘑鐮?
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!StringUtils.hasText(request.password()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 鏍￠獙鏂板瘑鐮佸己搴︼紝骞堕槻姝笌鏃у瘑鐮佷竴鑷?
        validatePassword(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "New password cannot be the same as old password");
        }

        // 鏇存柊瀵嗙爜
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", user.getId());
        wrapper.set("password_hash", passwordEncoder.encode(request.newPassword()));
        int update = usersMapper.update(wrapper);
        if (update == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }

        // 淇敼瀵嗙爜鍚庤鎵€鏈夎澶囬噸鏂扮櫥褰?
        bumpTokenVersion(user.getId());
        refreshTokenRedisStore.removeUserAllRefreshToken(user.getId());
        log.info("鐢ㄦ埛閲嶇疆瀵嗙爜鎴愬姛 - 鐢ㄦ埛ID: {}", user.getId());
    }

    /**
     * 瑙勮寖鍖栭偖绠辨牸寮?
     * 
     * <p>鍘婚櫎棣栧熬绌烘牸骞惰浆鎹负灏忓啓锛屼繚璇侀偖绠卞瓨鍌ㄥ拰鏌ヨ鐨勪竴鑷存€с€?
     * 
     * @param email 鍘熷閭
     * @return 瑙勮寖鍖栧悗鐨勯偖绠?
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 瑙勮寖鍖栫敤鎴峰悕鏍煎紡
     * 
     * <p>鍘婚櫎棣栧熬绌烘牸锛屼繚鎸佺敤鎴峰悕澶у皬鍐欏師鏍枫€?
     * 
     * @param username 鍘熷鐢ㄦ埛鍚?
     * @return 瑙勮寖鍖栧悗鐨勭敤鎴峰悕
     */
    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    /**
     * 楠岃瘉閭鏍煎紡
     * 
     * <p>妫€鏌ラ偖绠辨槸鍚︿负绌恒€佹槸鍚︾鍚堟爣鍑嗘牸寮忋€侀暱搴︽槸鍚﹀悎鐞嗐€?
     * 
     * @param email 閭鍦板潃
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
     * 楠岃瘉鐢ㄦ埛鍚嶆牸寮?
     * 
     * <p>妫€鏌ョ敤鎴峰悕鏄惁涓虹┖銆侀暱搴︽槸鍚﹀湪3-20涔嬮棿銆佹槸鍚﹀彧鍖呭惈瀛楁瘝鏁板瓧涓嬪垝绾裤€佹槸鍚︿互瀛楁瘝寮€澶淬€?
     * 
     * @param username 鐢ㄦ埛鍚?
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
     * 楠岃瘉瀵嗙爜鏍煎紡
     * 
     * <p>妫€鏌ュ瘑鐮佹槸鍚︿负绌恒€侀暱搴︽槸鍚︾鍚堥厤缃姹傘€佹槸鍚﹀悓鏃跺寘鍚瓧姣嶅拰鏁板瓧銆?
     * 
     * @param password 瀵嗙爜
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
     * 鑾峰彇鐢ㄦ埛褰撳墠浠ょ墝鐗堟湰鍙?
     * 
     * <p>浠嶳edis涓煡璇㈢敤鎴风殑浠ょ墝鐗堟湰鍙凤紝濡傛灉涓嶅瓨鍦ㄥ垯杩斿洖0銆?
     * 浠ょ墝鐗堟湰鍙风敤浜庡疄鐜板叏璁惧鐧诲嚭鍔熻兘銆?
     * 
     * @param userId 鐢ㄦ埛ID
     * @return 褰撳墠浠ょ墝鐗堟湰鍙?
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
     * 澧炲姞鐢ㄦ埛浠ょ墝鐗堟湰鍙?
     * 
     * <p>鍦≧edis涓€掑鐢ㄦ埛鐨勪护鐗岀増鏈彿锛岃繖鏍锋墍鏈夋棫浠ょ墝閮戒細澶辨晥锛?
     * 鐢ㄦ埛闇€瑕侀噸鏂扮櫥褰曟墠鑳借幏鍙栨柊鐨勪护鐗屻€?
     * 
     * @param userId 鐢ㄦ埛ID
     */
    private void bumpTokenVersion(Long userId) {
        redisTemplate.opsForValue().increment(USER_TOKEN_VERSION_KEY_PREFIX + userId);
    }

    /**
     * 灏嗙敤鎴锋按涔呭皝绂佺姸鎬佸啓鍏itmap锛屼究浜庨珮鏁堟煡璇?
     *
     * @param userId 鐢ㄦ埛ID
     */
    private void markUserBannedInBitmap(Long userId) {
        redisTemplate.opsForValue().setBit(USER_IS_BANNED_BITMAP_KEY, userId, true);
    }

    /**
     * 鍒ゆ柇鐢ㄦ埛鏄惁鍦ㄥ皝绂佷綅鍥句腑琚爣璁颁负绂佺敤
     *
     * @param userId 鐢ㄦ埛ID
     * @return true 琛ㄧず宸插皝绂?
     */
    private boolean isUserBannedByBitmap(Long userId) {
        Boolean banned = redisTemplate.opsForValue().getBit(USER_IS_BANNED_BITMAP_KEY, userId);
        return Boolean.TRUE.equals(banned);
    }
}


