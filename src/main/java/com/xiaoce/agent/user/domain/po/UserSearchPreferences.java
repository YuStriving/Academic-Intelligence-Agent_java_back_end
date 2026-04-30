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
 * 用户检索偏好
 * </p>
 *
 * @author 小策
 * @since 2026-04-27
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_search_preferences")
public class UserSearchPreferences implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * users.id
     */
    private Long userId;

    /**
     * 起始年份
     */
    private Integer yearStart;

    /**
     * 结束年份
     */
    private Integer yearEnd;

    /**
     * 最大结果数 1-10
     */
    private Integer maxResults;

    /**
     * 匹配度阈值 0-100
     */
    private Integer matchScore;

    /**
     * 检索源位图:1=GoogleScholar,2=arXiv,4=IEEE
     */
    private Integer sourceFlags;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 排序：1-相关度,2-最新,3-最早,4-引用最多
     */
    private Integer defaultSort;

    /**
     * 文献类型：0-全部,1-期刊,2-会议,3-预印本
     */
    private Integer docType;


}
