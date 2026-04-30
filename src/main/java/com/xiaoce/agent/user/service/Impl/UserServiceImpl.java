package com.xiaoce.agent.user.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.auth.domain.po.User;
import com.xiaoce.agent.auth.domain.vo.ValidateCodeResult;
import com.xiaoce.agent.auth.enums.ClientType;
import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;
import com.xiaoce.agent.auth.mapper.UsersMapper;
import com.xiaoce.agent.auth.service.impl.RefreshTokenStoreImpl;
import com.xiaoce.agent.auth.service.impl.ValidateCodeImpl;

import com.xiaoce.agent.user.domain.dto.UserAIConfigRequest;
import com.xiaoce.agent.user.domain.dto.UserDeletedRequest;
import com.xiaoce.agent.user.domain.dto.UserProfileRequest;
import com.xiaoce.agent.user.domain.dto.UserRetrieveRequest;
import com.xiaoce.agent.user.domain.po.AiConfig;
import com.xiaoce.agent.user.domain.po.UserAiDefaultConfig;
import com.xiaoce.agent.user.domain.po.UserSearchDefaultConfig;
import com.xiaoce.agent.user.domain.po.UserSearchPreferences;
import com.xiaoce.agent.user.domain.vo.UserAIConfigResponse;
import com.xiaoce.agent.user.domain.vo.UserProfileResponse;
import com.xiaoce.agent.user.domain.vo.UserRetrieveResponse;
import com.xiaoce.agent.user.mapper.AiConfigMapper;
import com.xiaoce.agent.user.mapper.UserSearchPreferencesMapper;
import com.xiaoce.agent.user.service.IUserService;
import com.xiaoce.agent.user.utils.AesEncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

import static com.xiaoce.agent.auth.common.constant.AuthConstant.EMAIL_PATTERN;
import static com.xiaoce.agent.auth.common.utils.UserInfoUtils.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements IUserService {
    private final UsersMapper usersMapper;
    private final ValidateCodeImpl validateCodeService;
    private final UserSearchPreferencesMapper userSearchPreferencesMapper;
    private final AiConfigMapper aiConfigMapper;
    private final RefreshTokenStoreImpl refreshTokenStore;

    @Value("${ai.encryption.secret-key}")
    private String encryptionSecretKey;

    @Override
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserProfileRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }
        if (request == null || UserProfileRequest.UserProfileRequestIsEmpty(request)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }
        User currentUser = usersMapper.selectById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户未找到");
        }
        String nickname = null;
        String avatarUrl = null;
        String academicId = null;
        String email = null;
        String bio = null;
        Integer gender = null;
        String school = null;

        boolean academicIdChanged = false;
        boolean emailChanged = false;

        if (request.nickname().isPresent()) {
            nickname = normalizePlainText(request.nickname().get());
        }
        if (request.avatarUrl().isPresent()) {
            avatarUrl = normalizePlainText(request.avatarUrl().get());
        }
        if (request.bio().isPresent()) {
            bio = normalizePlainText(request.bio().get());
        }
        if (request.school().isPresent()) {
            school = normalizePlainText(request.school().get());
        }
        if (request.gender().isPresent()) {
            gender = request.gender().get().getCode();
        }
        if (request.academicId().isPresent()) {
            String normalizedAcademicId = normalizeAcademicId(request.academicId().get());
            validateAcademicId(normalizedAcademicId);
            academicId = normalizedAcademicId;
            academicIdChanged = true;
        }
        if (request.email().isPresent()) {
            String rawEmail = request.email().get();
            if (!StringUtils.hasText(rawEmail)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱不能为空");
            }
            String normalizedEmail = normalizeEmail(rawEmail);
            validateEmail(normalizedEmail);
            email = normalizedEmail;
            emailChanged = true;
        }
        if (emailChanged) {
            String code = requireValidateCode(request);
            String currentEmail = currentUser.getEmail();
            if (currentEmail == null || !StringUtils.hasText(currentEmail)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "用户邮箱不存在");
            }
            verifyCodeOrThrow(currentEmail, ValidateCodeSendSceneEnums.UPDATEUSEREMAIL, code,
                    "邮箱不存在或者验证码无效"
            );
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime allowBefore = now.minusYears(1);
        try {
            int affected = usersMapper.updateProfileSelective(
                    userId,
                    nickname,
                    avatarUrl,
                    academicId,
                    email,
                    bio,
                    gender,
                    school,
                    academicIdChanged,
                    allowBefore,
                    now
            );
            if (affected == 0) {
                if (academicIdChanged) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "学术id一年只可以修改一次");
                }
                throw new BusinessException(ErrorCode.NOT_FOUND, "用户未找到");
            }
        } catch (DuplicateKeyException e) {
            if (emailChanged) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS, "邮箱已经存在");
            }
            if (academicIdChanged) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "学术id已经存在");
            }
            throw e;
        }
        User latestUser = usersMapper.selectById(userId);
        if (latestUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户未找到");
        }
        return UserProfileResponse.toUserProfileResponse(latestUser, latestUser.getAcademicIdLastModified());
    }

    @Override
    @Transactional
    public UserRetrieveResponse retrieveProfile(Long userId) {
        if(userId == null){
            throw new BusinessException(ErrorCode.BAD_REQUEST, "user id is required");
        }
        // TODO 后续考虑增加redis存储用户的配置信息，增加读取效率，降低数据库的压力

        // 直接去查询用户的配置信息
        LambdaQueryWrapper<UserSearchPreferences> query = new LambdaQueryWrapper<>();
        query.eq(UserSearchPreferences::getUserId, userId);
        UserSearchPreferences userSearchPreferences = userSearchPreferencesMapper.selectOne(query);
        if (userSearchPreferences == null) {
            // 返回默认的配置
            return UserRetrieveResponse.toUserRetrieveDefaultResponse(userId, new UserSearchDefaultConfig());
        }
        return UserRetrieveResponse.toUserRetrieveResponse(userId, userSearchPreferences);
    }

    @Override
    @Transactional
    public UserRetrieveResponse updateRetrievePreferences(Long userId, UserRetrieveRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "user id is required");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "request body is required");
        }
        LambdaQueryWrapper<UserSearchPreferences> query = new LambdaQueryWrapper<>();
        query.eq(UserSearchPreferences::getUserId, userId);
        UserSearchPreferences existingPrefs = userSearchPreferencesMapper.selectOne(query);

        UserSearchPreferences prefsToUpdate;
        if (existingPrefs != null) {
            prefsToUpdate = existingPrefs;
        } else {
            prefsToUpdate = new UserSearchPreferences();
            prefsToUpdate.setUserId(userId);
        }
        if (request.yearStart().isPresent()) {
            prefsToUpdate.setYearStart(request.yearStart().get());
        }
        if (request.yearEnd().isPresent()) {
            prefsToUpdate.setYearEnd(request.yearEnd().get());
        }
        if (request.maxResults().isPresent()) {
            prefsToUpdate.setMaxResults(request.maxResults().get());
        }
        if (request.matchScore().isPresent()) {
            prefsToUpdate.setMatchScore(request.matchScore().get());
        }
        if (request.sourceFlags().isPresent()) {
            prefsToUpdate.setSourceFlags(request.sourceFlags().get());
        }
        if (request.defaultSort().isPresent()) {
            prefsToUpdate.setDefaultSort(request.defaultSort().get().getCode());
        }
        if (request.docType().isPresent()) {
            prefsToUpdate.setDocType(request.docType().get().getCode());
        }
        LocalDateTime now = LocalDateTime.now();
        prefsToUpdate.setUpdatedAt(now);

        if (existingPrefs != null) {
            userSearchPreferencesMapper.updateById(prefsToUpdate);
        } else {
            prefsToUpdate.setCreatedAt(now);
            userSearchPreferencesMapper.insert(prefsToUpdate);
        }
        return UserRetrieveResponse.toUserRetrieveResponse(userId, prefsToUpdate);
    }

    @Override
    public UserAIConfigResponse getAiRAGConfig(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }

        LambdaQueryWrapper<AiConfig> query = new LambdaQueryWrapper<>();
        query.eq(AiConfig::getUserId, userId);
        AiConfig config = aiConfigMapper.selectOne(query);
        if (config == null) {
            return UserAIConfigResponse.toUserDefaultAIConfigResponse(new UserAiDefaultConfig(), null);
        }

        String maskedApiKey = maskApiKey(config.getApiKeyEncrypted());
        return UserAIConfigResponse.toUserAIConfigResponse(config, maskedApiKey);
    }

    @Override
    @Transactional
    public UserAIConfigResponse updateAiRAGConfig(Long userId, UserAIConfigRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }

        LambdaQueryWrapper<AiConfig> query = new LambdaQueryWrapper<>();
        query.eq(AiConfig::getUserId, userId);
        AiConfig existing = aiConfigMapper.selectOne(query);

        LocalDateTime now = LocalDateTime.now();

        if (existing != null) {
            patchUpdateAiConfig(existing, request, now);
            aiConfigMapper.updateById(existing);
        } else {
            AiConfig newConfig = createNewConfigFromRequest(userId, request, now);
            aiConfigMapper.insert(newConfig);
            existing = newConfig;
        }

        String maskedApiKey = maskApiKey(existing.getApiKeyEncrypted());
        return UserAIConfigResponse.toUserAIConfigResponse(existing, maskedApiKey);
    }

    @Override
    public void deleteUser(Long userId, UserDeletedRequest request, ClientType clientType) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }
        String emailOrUserName = request.emailOrUserName();
        if (StringUtils.isEmpty(emailOrUserName) || !StringUtils.hasText(request.validateCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求参数错误");
        }
        // 查询用户是否存在
        User user = null;
        if (EMAIL_PATTERN.matcher(emailOrUserName).matches()) {
            validateEmail(emailOrUserName);
            user = usersMapper.findByEmail(emailOrUserName);
        }else {
            validateUsername(emailOrUserName);
            user = usersMapper.findByUsername(emailOrUserName);
        }
        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在");
        }
        // 校验用户的验证码
        ValidateCodeResult result = validateCodeService.verifyCode(user.getEmail(), ValidateCodeSendSceneEnums.DELETEACCOUNT, request.validateCode());
        if (!result.isSuccess()){
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误");
        }
        // 删除用户，采用标记删除
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", userId);
        updateWrapper.set("is_deleted", 1);
        int update = usersMapper.update(null, updateWrapper);
        if (update == 0) {
          throw new BusinessException(ErrorCode.BAD_REQUEST, "删除用户失败");
          //TODO 后续考虑使用消息队列再去重试异步删除
        }
        // 清理当前用户的全部令牌
        refreshTokenStore.removeUserAllRefreshToken(userId,clientType);
    }


    private String requireValidateCode(UserProfileRequest request) {
        if (request.validateCode().isEmpty() || !StringUtils.hasText(request.validateCode().get())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码不能为空");
        }
        return request.validateCode().get().trim();
    }

    private void verifyCodeOrThrow(String email, ValidateCodeSendSceneEnums scene, String code, String errorMessage) {
        ValidateCodeResult result = validateCodeService.verifyCode(email, scene, code);
        if (!result.isSuccess()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, errorMessage);
        }
    }

    private String normalizePlainText(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * API 密钥脱敏处理
     * <p>
     * 规则：前3位 + **** + 后4位
     * 示例：sk-abc123456789xyz → sk-***8xyz
     *
     * @param encryptedKey 加密后的 API Key
     * @return 脱敏后的 API Key，如果为空或解密失败返回 null
     */
    private String maskApiKey(String encryptedKey) {
        if (!StringUtils.hasText(encryptedKey)) {
            return null;
        }

        try {
            String plainKey = AesEncryptUtil.decrypt(encryptedKey, encryptionSecretKey);

            if (plainKey.length() <= 8) {
                return "****";
            }

            return plainKey.substring(0, 3) + "****" + plainKey.substring(plainKey.length() - 4);
        } catch (Exception e) {
            log.warn("API密钥脱敏失败, userId may have invalid key", e);
            return "****";
        }
    }

    /**
     * 部分更新已有的 AI 配置（Patch 语义）
     * <p>
     * 只有 request 中非 null 的字段才会被更新
     * 支持前端只修改部分配置项
     *
     * @param config  已有的配置实体
     * @param request 前端请求（只包含需要更新的字段）
     * @param now     当前时间
     */
    private void patchUpdateAiConfig(AiConfig config, UserAIConfigRequest request, LocalDateTime now) {
        boolean updated = false;

        if (request.getModelProvider() != null) {
            config.setModelProvider(request.getModelProvider());
            updated = true;
        }
        if (request.getMaxContextPapers() != null) {
            config.setMaxContextPapers(request.getMaxContextPapers());
            updated = true;
        }
        if (request.getResponseLengthLimit() != null) {
            config.setResponseLengthLimit(request.getResponseLengthLimit());
            updated = true;
        }
        if (request.getCitationFormat() != null) {
            config.setCitationFormat(request.getCitationFormat());
            updated = true;
        }
        if (request.getIncludeCitation() != null) {
            config.setIncludeCitation(request.getIncludeCitation());
            updated = true;
        }

        if (StringUtils.hasText(request.getApiKey())) {
            String encryptedKey = AesEncryptUtil.encrypt(request.getApiKey(), encryptionSecretKey);
            config.setApiKeyEncrypted(encryptedKey);
            updated = true;
            log.info("用户更新了AI配置的API Key, userId={}", config.getUserId());
        }

        if (updated) {
            config.setVersion(config.getVersion() + 1);
            config.setUpdatedAt(now);
        }
    }

    /**
     * 从请求创建新的 AI 配置（首次创建时使用默认值填充未传字段）
     *
     * @param userId  用户ID
     * @param request 前端请求（可能只包含部分字段）
     * @param now     当前时间
     * @return 新创建的配置实体（未传字段使用默认值）
     */
    private AiConfig createNewConfigFromRequest(Long userId, UserAIConfigRequest request, LocalDateTime now) {
        AiConfig config = new AiConfig();
        config.setUserId(userId);

        config.setModelProvider(request.getModelProvider() != null ? request.getModelProvider() : 1);
        config.setMaxContextPapers(request.getMaxContextPapers() != null ? request.getMaxContextPapers() : 3);
        config.setResponseLengthLimit(request.getResponseLengthLimit() != null ? request.getResponseLengthLimit() : 2000);
        config.setCitationFormat(request.getCitationFormat() != null ? request.getCitationFormat() : 1);
        config.setIncludeCitation(request.getIncludeCitation() != null ? request.getIncludeCitation() : false);
        config.setVersion(0);
        config.setCreatedAt(now);
        config.setUpdatedAt(now);

        if (StringUtils.hasText(request.getApiKey())) {
            String encryptedKey = AesEncryptUtil.encrypt(request.getApiKey(), encryptionSecretKey);
            config.setApiKeyEncrypted(encryptedKey);
            log.info("用户新建了AI配置并设置API Key, userId={}", userId);
        }

        return config;
    }
}
