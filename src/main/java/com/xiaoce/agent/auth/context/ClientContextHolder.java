package com.xiaoce.agent.auth.context;

import com.xiaoce.agent.auth.enums.ClientType;

/**
 * 客户端类型上下文持有者（ThreadLocal）
 *
 * <p>使用 ThreadLocal 在当前线程中存储客户端类型信息，
 * 便于在 Controller、Service 等各层获取当前请求的客户端类型。
 *
 * <p>使用场景：
 * <ul>
 *   <li>记录日志：区分不同客户端的访问日志</li>
 *   <li>统计分析：统计各客户端的使用情况</li>
 *   <li>差异化处理：根据客户端类型返回不同格式的数据</li>
 *   <li>安全策略：对不同客户端实施不同的安全策略</li>
 * </ul>
 *
 * <p>⚠️ 重要提示：
 * <ul>
 *   <li>必须在请求结束时调用 {@link #clear()} 清理 ThreadLocal，防止内存泄漏</li>
 *   <li>建议在 Filter/Interceptor 的 afterCompletion 中清理</li>
 * </ul>
 *
 * @author 小策
 * @date 2026/4/27
 */
public final class ClientContextHolder {

    private static final ThreadLocal<ClientType> CLIENT_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();

    private ClientContextHolder() {
        // 私有构造函数，防止实例化
    }

    /**
     * 设置当前线程的客户端类型
     *
     * @param clientType 客户端类型枚举值
     */
    public static void set(ClientType clientType) {
        if (clientType != null) {
            CLIENT_TYPE.set(clientType);
        }
    }

    /**
     * 获取当前线程的客户端类型
     *
     * @return 客户端类型枚举值，如果未设置则返回 null
     */
    public static ClientType get() {
        return CLIENT_TYPE.get();
    }

    /**
     * 获取当前线程的客户端类型代码
     *
     * @return 客户端类型代码（如 "WEB"、"IOS_APP"），如果未设置则返回 null
     */
    public static String getCode() {
        ClientType clientType = CLIENT_TYPE.get();
        return clientType != null ? clientType.getCode() : null;
    }

    /**
     * 检查当前线程是否已设置客户端类型
     *
     * @return true=已设置, false=未设置
     */
    public static boolean isPresent() {
        return CLIENT_TYPE.get() != null;
    }

    /**
     * 检查当前客户端是否为指定类型
     *
     * @param targetType 目标客户端类型
     * @return true=是, false=否或未设置
     */
    public static boolean is(ClientType targetType) {
        ClientType current = CLIENT_TYPE.get();
        return current != null && current == targetType;
    }

    /**
     * 检查当前客户端是否为 Web 端（PC 或移动端网页）
     *
     * @return true=Web端, false=非Web端或未设置
     */
    public static boolean isWebClient() {
        ClientType current = CLIENT_TYPE.get();
        return current == ClientType.WEB || current == ClientType.MOBILE_WEB;
    }

    /**
     * 检查当前客户端是否为移动端（移动端网页或 App）
     *
     * @return true=移动端, false=非移动端或未设置
     */
    public static boolean isMobileClient() {
        ClientType current = CLIENT_TYPE.get();
        return current == ClientType.MOBILE_WEB
            || current == ClientType.IOS_APP
            || current == ClientType.ANDROID_APP;
    }

    /**
     * 设置当前请求的客户端 IP
     */
    public static void setIp(String ip) {
        CLIENT_IP.set(ip);
    }

    /**
     * 获取当前请求的客户端 IP
     */
    public static String getIp() {
        return CLIENT_IP.get();
    }

    // ========== 清理 ==========
    /**
     * 清理当前线程的客户端类型和 IP，防止内存泄漏
     */
    public static void clear() {
        CLIENT_TYPE.remove();
        CLIENT_IP.remove();
    }
}
