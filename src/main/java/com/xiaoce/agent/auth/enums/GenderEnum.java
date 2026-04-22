package com.xiaoce.agent.auth.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户性别枚举
 * 编码约定：0 男，1 女，2 未知（预留扩展位）
 */
@Getter
@AllArgsConstructor
public enum GenderEnum {

    MALE(0, "男"),
    FEMALE(1, "女"),
    UNKNOWN(2, "未知/未填写");

    private final Integer code;
    private final String desc;

    /**
     * 核心注解！JSON序列化专用
     * 后端接口返回给前端时，**直接返回数字code（0/1/2）**
     * 和你原来前后端约定完全一致！前端零代码改动、完全无缝兼容
     */
    @JsonValue
    public Integer getCode() {
        return this.code;
    }

    /**
     * 数据库/入参反向转换：根据数字编码获取枚举对象
     */
    public static GenderEnum getByCode(Integer code) {
        for (GenderEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        // 非法值兜底返回未知
        return UNKNOWN;
    }
}