package com.xiaoce.agent.user.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * AI配置表
 * </p>
 *
 * @author 小策
 * @since 2026-04-27
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("ai_config")
public class AiConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID，NULL表示系统默认配置
     */
    private Long userId;

    /**
     * 大模型：1-DeepSeek,2-Tongyi
     */
    private Integer modelProvider;

    /**
     * 最大上下文论文数
     */
    private Integer maxContextPapers;

    /**
     * 响应长度限制（字符数）
     */
    private Integer responseLengthLimit;

    /**
     * 加密后的API密钥
     */
    private String apiKeyEncrypted;

    /**
     * 引用格式：1-APA,2-MLA,3-Chicago,4-IEEE
     */
    private Integer citationFormat;

    /**
     * 是否包含引用：0-否,1-是
     */
    private Boolean includeCitation;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


}
