package com.xiaoce.agent.auth.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GenderEnum {
    MALE(0, "男"),
    FEMALE(1, "女"),
    UNKNOWN(2, "未知");

    private final Integer code;
    private final String desc;

    @JsonValue
    public Integer getCode() {
        return this.code;
    }

    public static GenderEnum getByCode(Integer code) {
        for (GenderEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return UNKNOWN;
    }
}
