package com.xiaoce.agent.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 排序方式枚举
 *
 * @author 小策
 * @date 2026/4/27 12:08
 */
@Getter
@AllArgsConstructor
public enum RetrieveSortEnums {

    RELEVANCE(1, "relevance", "相关度"),
    NEWEST(2, "date_desc", "最新优先"),
    OLDEST(3, "date_asc", "最早优先"),
    MOST_CITED(4, "citations", "引用最多");

    /**
     * 数据库存储值（整数）
     */
    private final Integer code;

    /**
     * 前端交互值（英文标识符）
     */
    private final String value;

    /**
     * 显示名称（中文）
     */
    private final String description;

    /**
     * 根据code获取枚举
     *
     * @param code 数据库中的整数值
     * @return 对应的枚举，如果未找到返回RELEVANCE（默认值）
     */
    public static RetrieveSortEnums getByCode(Integer code) {
        if (code == null) {
            return RELEVANCE;
        }
        for (RetrieveSortEnums e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return RELEVANCE; // 默认返回相关度排序
    }



}
