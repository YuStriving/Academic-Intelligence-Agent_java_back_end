package com.xiaoce.agent.auth.enums;

/**
 * UserIsDeleted
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/29 15:49
 */
public enum UserIsDeleted {

    NO(0, "未删除"),
    YES(1, "已删除");

    private Integer code;

    private String desc;

    UserIsDeleted(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }



}
