package com.xiaoce.agent.user.domain.vo;

import com.xiaoce.agent.user.domain.po.AiConfig;
import com.xiaoce.agent.user.domain.po.UserAiDefaultConfig;
import com.xiaoce.agent.user.enums.CitationFormatEnum;
import com.xiaoce.agent.user.enums.ModelProviderEnum;

import javax.swing.text.MaskFormatter;

/**
 * AI配置响应对象（脱敏版）
 * <p>
 * 安全原则：API 密钥采用脱敏处理，只返回部分掩码信息
 * 前端展示格式：sk-****xxxx
 *
 * @author 小策
 * @date 2026/4/28 19:37
 */
public record UserAIConfigResponse(
        ModelProviderEnum modelName,
        Integer maxContextPapers,
        Integer responseLengthLimit,
        String maskedApiKey,       // 脱敏后的 API Key：sk-****xxxx
        CitationFormatEnum citationFormat,
        boolean includeCitation
) {
    /**
     * 从实体对象构建响应（含脱敏逻辑）
     *
     * @param config      AI 配置实体（包含加密的 API Key）
     * @param maskedApiKey 脱敏处理后的 API Key（由 Service 层传入）
     */
    public static UserAIConfigResponse toUserAIConfigResponse(AiConfig config,String maskedApiKey) {
        return new UserAIConfigResponse(
                ModelProviderEnum.fromValue(config.getModelProvider()),
                config.getMaxContextPapers(),
                config.getResponseLengthLimit(),
                maskedApiKey,
                CitationFormatEnum.fromValue(config.getCitationFormat()),
                Boolean.TRUE.equals(config.getIncludeCitation())
        );
    }
    public static UserAIConfigResponse toUserDefaultAIConfigResponse(UserAiDefaultConfig config,  String maskedApiKey) {
        return new UserAIConfigResponse(
                ModelProviderEnum.fromValue(config.getModelProvider()),
                config.getMaxContextPapers(),
                config.getResponseLengthLimit(),
                maskedApiKey,
                CitationFormatEnum.fromValue(config.getCitationFormat()),
                Boolean.TRUE.equals(config.getIncludeCitation()));
    }
}
