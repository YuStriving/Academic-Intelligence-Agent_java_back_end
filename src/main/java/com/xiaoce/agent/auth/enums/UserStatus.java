package com.xiaoce.agent.auth.enums;

/**
 * UserStatus
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/22 19:19
 */
public enum UserStatus {
    ENABLE(1, "启用"),
    DISABLE(0, "禁用");
    private int code;
    private String desc;
    UserStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public int getCode() {
        return code;
    }
    public String getDesc() {
        return desc;
    }

}
