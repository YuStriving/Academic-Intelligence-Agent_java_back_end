package com.xiaoce.agent.user.service;

import com.xiaoce.agent.auth.enums.ClientType;
import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;
import com.xiaoce.agent.user.domain.dto.UserDeletedRequest;
import com.xiaoce.agent.user.domain.dto.UserProfileRequest;
import com.xiaoce.agent.user.domain.dto.UserRetrieveRequest;
import com.xiaoce.agent.user.domain.dto.UserAIConfigRequest;
import com.xiaoce.agent.user.domain.po.AiConfig;
import com.xiaoce.agent.user.domain.vo.UserAIConfigResponse;
import com.xiaoce.agent.user.domain.vo.UserProfileResponse;
import com.xiaoce.agent.user.domain.vo.UserRetrieveResponse;
import jakarta.validation.Valid;

/**
 * IUserService
 * <p>
 * 用户服务接口，提供用户资料、搜索偏好、AI配置等管理功能
 *
 * @author 小策
 * @date 2026/4/25 20:18
 */
public interface IUserService {

    UserProfileResponse updateProfile(Long userId, @Valid UserProfileRequest request);

    UserRetrieveResponse retrieveProfile(Long userId);

    UserRetrieveResponse updateRetrievePreferences(Long userId, @Valid UserRetrieveRequest request);

    /**
     * 获取用户 AI 配置（RAG & AI）
     *
     * @param userId 用户ID
     * @return AI 配置信息（API Key 已脱敏）
     */
    UserAIConfigResponse getAiRAGConfig(Long userId);

    /**
     * 更新用户 AI 配置（RAG & AI）
     * <p>
     * 支持创建新配置或更新已有配置
     * API 密钥会自动加密存储
     *
     * @param userId  用户ID
     * @param request AI 配置请求（包含明文 API Key）
     * @return 更新后的 AI 配置信息（API Key 已脱敏）
     */
    UserAIConfigResponse updateAiRAGConfig(Long userId, @Valid UserAIConfigRequest request);

    void deleteUser(Long userId, UserDeletedRequest request, ClientType clientType);
}
