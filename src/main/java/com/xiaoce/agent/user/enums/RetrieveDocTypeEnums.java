package com.xiaoce.agent.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文献类型枚举
 *
 * @author 小策
 * @date 2026/4/27 12:10
 */
@Getter
@AllArgsConstructor
public enum RetrieveDocTypeEnums {

    ALL(0, "all", "全部类型"),
    JOURNAL(1, "journal", "期刊论文"),
    CONFERENCE(2, "conference", "会议论文"),
    PREPRINT(3, "preprint", "预印本");

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
     * @return 对应的枚举，如果未找到返回ALL（默认值）
     */
    public static RetrieveDocTypeEnums getByCode(Integer code) {
        if (code == null) {
            return ALL;
        }
        for (RetrieveDocTypeEnums e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return ALL; // 默认返回全部
    }



}
