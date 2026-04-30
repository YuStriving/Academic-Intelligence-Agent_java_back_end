package com.xiaoce.agent.auth.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 验证码发送场景枚举
 * <p>
 * 定义系统中所有需要发送验证码的业务场景，用于区分不同业务流程中的验证码
 *
 * @author 小策
 * @date 2026/4/24 19:32
 */
@AllArgsConstructor
@Getter
public enum ValidateCodeSendSceneEnums {
    /** 用户注册 */
    REGISTER("REGISTER", "用户注册"),

    /** 忘记密码 */
    FORGETPASSWORD("FORGETPASSWORD", "忘记密码"),

    /** 重置密码（已登录状态） */
    RESETPASSWORD("RESETPASSWORD", "重置密码"),

    /** 更新学术ID */
    UPDATEUSERACADEMICID("UPDATEUSERACADEMICID", "更新学术ID"),

    /** 更新邮箱 */
    UPDATEUSEREMAIL("UPDATEUSEREMAIL", "更新邮箱"),
    /**删除账户**/
    DELETEACCOUNT("DELETEACCOUNT", "删除账号");

    private final String code;
    private final String description;

    /**
     * 支持从字符串或枚举名称反序列化
     * 兼容前端的多种传参方式：
     * - 字符串格式："UPDATEUSEREMAIL"
     * - 枚举名格式："UPDATEUSEREMAIL"
     */
    @JsonCreator
    public static ValidateCodeSendSceneEnums fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        
        String trimmedValue = value.trim().toUpperCase();
        
        for (ValidateCodeSendSceneEnums scene : values()) {
            if (scene.name().equals(trimmedValue) || 
                scene.code.equals(trimmedValue)) {
                return scene;
            }
        }
        
        throw new IllegalArgumentException("未知的验证码场景: " + value);
    }

    /**
     * 根据 code 查找枚举值
     *
     * @param code 场景代码
     * @return 对应的枚举值，如果不匹配则返回 null
     */
    public static ValidateCodeSendSceneEnums fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ValidateCodeSendSceneEnums scene : values()) {
            if (scene.code.equalsIgnoreCase(code.trim())) {
                return scene;
            }
        }
        return null;
    }
}
