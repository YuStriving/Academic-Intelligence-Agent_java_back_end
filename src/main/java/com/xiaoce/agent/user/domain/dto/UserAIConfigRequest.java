package com.xiaoce.agent.user.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * AI配置请求对象（Patch 部分更新）
 * <p>
 * 用于接收前端提交的 RAG & AI 配置信息
 * 所有字段均为可选，支持部分更新（Patch 语义）
 * 只有非 null 的字段才会被更新到数据库
 *
 * @author 小策
 * @date 2026/4/28
 */
@Data
public class UserAIConfigRequest {

    /**
     * 大模型提供商：1-DeepSeek, 2-Tongyi（可选）
     */
    @Min(value = 1, message = "无效的大模型类型")
    @Max(value = 2, message = "无效的大模型类型")
    private Integer modelProvider;

    /**
     * 最大上下文论文数（可选，默认3篇）
     */
    @Min(value = 1, message = "最小为1篇")
    @Max(value = 10, message = "最大为10篇")
    private Integer maxContextPapers;

    /**
     * 响应长度限制（可选，默认2000字符）
     */
    @Min(value = 100, message = "最小100字符")
    @Max(value = 10000, message = "最大10000字符")
    private Integer responseLengthLimit;

    /**
     * API 密钥（明文，后端会加密存储，可选）
     * 不传或传空字符串表示不更新此字段
     */
    private String apiKey;

    /**
     * 引用格式：1-APA, 2-MLA, 3-Chicago, 4-IEEE（可选）
     */
    @Min(value = 1, message = "无效的引用格式")
    @Max(value = 4, message = "无效的引用格式")
    private Integer citationFormat;

    /**
     * 是否包含引用：true-是, false-否（可选）
     */
    private Boolean includeCitation;
}
