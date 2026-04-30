package com.xiaoce.agent.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 大模型提供商枚举
 * <p>
 * 支持的大模型类型：
 * - DeepSeek：深度求索的对话模型
 * - Tongyi：阿里云通义千问模型
 *
 * @author 小策
 * @date 2026/4/27 14:52
 */
@Getter
@AllArgsConstructor
public enum ModelProviderEnum {

    DEEPSEEK(1, "DeepSeek"),
    TONGYI(2, "Tongyi");

    private final int code;
    private final String name;

    /**
     * 根据code获取枚举
     *
     * @param code 枚举编码
     * @return 对应的枚举值，如果不存在返回null
     */
    public static ModelProviderEnum fromCode(int code) {
        for (ModelProviderEnum value : values()) {
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
    public static ModelProviderEnum fromValue(Object value) {
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
            for (ModelProviderEnum model : values()) {
                if (model.getName().equalsIgnoreCase(strValue)) {
                    return model;
                }
            }
        }

        throw new IllegalArgumentException("未知的ModelProvider: " + value);
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
