package com.xiaoce.agent.auth.enums;

/**
 * 客户端类型枚举
 *
 * <p>用于标识请求来源的客户端类型，通过 HTTP Header {@code X-Client-Type} 传递。
 *
 * <p>支持的客户端类型：
 * <ul>
 *   <li>{@link #WEB} - PC端网页浏览器</li>
 *   <li>{@link #MOBILE_WEB} - 移动端网页浏览器（手机/平板）</li>
 *   <li>{@link #IOS_APP} - iOS原生应用</li>
 *   <li>{@link #ANDROID_APP} - Android原生应用</li>
 *   <li>{@link #MINI_PROGRAM} - 微信小程序</li>
 *   <li>{@link #DESKTOP_APP} - 桌面客户端（Electron等）</li>
 * </ul>
 *
 * @author 小策
 * @date 2026/4/27
 */
public enum ClientType {

    /** PC端网页浏览器（Chrome、Firefox、Edge等） */
    WEB("WEB", "PC端网页"),

    /** 移动端网页浏览器（手机/平板浏览器） */
    MOBILE_WEB("MOBILE_WEB", "移动端网页"),

    /** iOS原生应用 */
    IOS_APP("IOS_APP", "iOS应用"),

    /** Android原生应用 */
    ANDROID_APP("ANDROID_APP", "Android应用"),

    /** 微信小程序 */
    MINI_PROGRAM("MINI_PROGRAM", "微信小程序"),

    /** 桌面客户端（Electron、Tauri等） */
    DESKTOP_APP("DESKTOP_APP", "桌面客户端");

    private final String code;
    private final String description;

    ClientType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 查找枚举值
     *
     * @param code 客户端类型代码（如 "WEB"、"IOS_APP"）
     * @return 对应的枚举值，如果不匹配则返回 null
     */
    public static ClientType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ClientType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return null;
    }

    /**
     * 验证是否为合法的客户端类型代码
     *
     * @param code 待验证的代码
     * @return true=合法, false=非法
     */
    public static boolean isValidCode(String code) {
        return fromCode(code) != null;
    }
}
