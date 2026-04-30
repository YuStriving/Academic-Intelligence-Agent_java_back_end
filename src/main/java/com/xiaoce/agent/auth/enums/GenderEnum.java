package com.xiaoce.agent.auth.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GenderEnum {
    UNKNOWN(0, "未知"),
    MALE(1, "男"),
    FEMALE(2, "女");

    private final Integer code;
    private final String desc;

    @JsonValue
    public Integer getCode() {
        return this.code;
    }

    @JsonCreator
    public static GenderEnum fromValue(Object value) {
        if (value == null) {
            return UNKNOWN;
        }

        String strValue = value.toString().trim();

        try {
            int code = Integer.parseInt(strValue);
            return getByCode(code);
        } catch (NumberFormatException e) {
            for (GenderEnum gender : values()) {
                if (gender.name().equalsIgnoreCase(strValue) || 
                    gender.desc.equals(strValue)) {
                    return gender;
                }
            }
        }

        return UNKNOWN;
    }

    public static GenderEnum getByCode(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        for (GenderEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return UNKNOWN;
    }
}
