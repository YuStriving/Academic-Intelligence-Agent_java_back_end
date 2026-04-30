package com.xiaoce.agent.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 引用格式枚举
 * <p>
 * 支持的学术引用格式：
 * - APA：美国心理学会格式（社会科学常用）
 * - MLA：现代语言协会格式（人文学科常用）
 * - Chicago：芝加哥大学格式（历史、艺术常用）
 * - IEEE：电气电子工程师学会格式（工程、技术常用）
 *
 * @author 小策
 * @date 2026/4/27 14:51
 */
@Getter
@AllArgsConstructor
public enum CitationFormatEnum {

    APA(1, "APA"),
    MLA(2, "MLA"),
    CHICAGO(3, "Chicago"),
    IEEE(4, "IEEE");

    private final int code;
    private final String name;

    /**
     * 根据code获取枚举
     *
     * @param code 枚举编码
     * @return 对应的枚举值，如果不存在返回null
     */
    public static CitationFormatEnum fromCode(int code) {
        for (CitationFormatEnum value : values()) {
            if (value.getCode() == code) {
                return value;
            }
        }
        return null;
    }

    /**
     * Jackson反序列化：根据code字符串转换为枚举
     * 支持前端传递数字或字符串格式
     *
     * @param value 前端传入的值（数字或名称）
     * @return 对应的枚举实例
     */
    @JsonCreator
    public static CitationFormatEnum fromValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return fromCode(((Number) value).intValue());
        }

        String strValue = value.toString().trim();
        try {
            return fromCode(Integer.parseInt(strValue));
        } catch (NumberFormatException e) {
            // 尝试按名称匹配（不区分大小写）
            for (CitationFormatEnum format : values()) {
                if (format.getName().equalsIgnoreCase(strValue)) {
                    return format;
                }
            }
        }

        throw new IllegalArgumentException("未知的CitationFormat: " + value);
    }

    /**
     * Jackson序列化：输出code值给前端
     *
     * @return 枚举编码
     */
    @JsonValue
    public int getCodeValue() {
        return this.code;
    }
}
