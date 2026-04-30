package com.xiaoce.agent.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.xiaoce.agent.user.enums.RetrieveDocTypeEnums;
import com.xiaoce.agent.user.enums.RetrieveSortEnums;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UserSearchDefaultConfig
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/27 11:45
 */
@Data
public class UserSearchDefaultConfig {
    /**
     * 起始年份
     */
    private Integer yearStart = 2020;

    /**
     * 结束年份
     */
    private Integer yearEnd = 2026;

    /**
     * 最大结果数 1-10
     */
    private Integer maxResults = 5;

    /**
     * 匹配度阈值 0-100
     */
    private Integer matchScore = 70;

    /**
     * 检索源位图:1=GoogleScholar,2=arXiv,4=IEEE
     */
    private Integer sourceFlags = 7;


    private RetrieveSortEnums defaultSort = RetrieveSortEnums.RELEVANCE;

    /**
     * ALL|JOURNAL|CONFERENCE|PREPRINT
     */
    private RetrieveDocTypeEnums docType = RetrieveDocTypeEnums.ALL;

}
