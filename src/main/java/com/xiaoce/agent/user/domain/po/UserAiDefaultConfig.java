package com.xiaoce.agent.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.xiaoce.agent.user.enums.CitationFormatEnum;
import com.xiaoce.agent.user.enums.ModelProviderEnum;
import lombok.Data;

/**
 * UserAiDefaultConfig
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/27 14:50
 */
@Data
public class UserAiDefaultConfig {

    /**
     * 大模型：1-DeepSeek,2-Tongyi
     */
    private ModelProviderEnum modelProvider = ModelProviderEnum.DEEPSEEK;

    /**
     * 最大上下文论文数
     */
    private Integer maxContextPapers = 5;

    /**
     * 响应长度限制（字符数）
     */
    private Integer responseLengthLimit = 2000;

    /**
     * 加密后的API密钥
     */
    private String apiKeyEncrypted = "";

    /**
     * 引用格式：1-APA,2-MLA,3-Chicago,4-IEEE
     */
    private CitationFormatEnum citationFormat = CitationFormatEnum.APA;

    /**
     * 是否包含引用：0-否,1-是
     */
    private Boolean includeCitation = true;

}
