package com.xiaoce.agent.auth.enums;

import lombok.Getter;

/**
 * 验证码返回状态枚举。
 * <p>
 * 对应：成功、未找到、过期、不匹配、尝试次数过多。
 */
@Getter
public enum VerificationCodeStatus {
    /**
     * 校验通过。
     * 表示用户输入的验证码与系统存储的一致且未过期。
     */
    VALID(1, "验证码校验通过"),

    /**
     * 验证码不存在或已过期。
     * 通常是因为用户超过了规定时间（如5分钟）才输入，或者根本没点击发送。
     */
    EXPIRED(2, "验证码已过期或不存在"),

    /**
     * 验证码错误。
     * 表示验证码还在有效期内，但用户输入的数字不正确。
     */
    INVALID(3, "验证码输入错误"),

    /**
     * 发送频率过快。
     * 用于风控，比如限制 60 秒内只能发送一次。
     */
    TOO_FREQUENT(4, "发送请求过于频繁，请稍后再试");

    /**
     * 状态码：用于逻辑判断或存入数据库。
     */
    private final int code;

    /**
     * 状态描述：用于给前端展示或日志记录。
     */
    private final String message;
    VerificationCodeStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }
    /**
     * 根据状态码查找对应的枚举对象。
     * * @param code 状态数值
     * @return 匹配的枚举实例，若无匹配则返回 null
     */
    public static Integer fromCode(int code) {
        for (VerificationCodeStatus status : VerificationCodeStatus.values()) {
            if (status.getCode() == code) {
                return status.code;
            }
        }
        return null;
    }
}
