package com.xiaoce.agent.auth.common.utils;

import com.xiaoce.agent.auth.domain.dto.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 获取客户端信息
 * <p>
 */
public class GetClientInfoUtils {
    public static ClientInfo resolveClientInfo(HttpServletRequest request) {
        String ip = extractClientIp(request);
        request.getHeader("User-Agent");
        return new ClientInfo(ip, request.getHeader("User-Agent"));
    }

    public static String extractClientIp(HttpServletRequest request) {
        // 1. 优先查标准代理头 "X-Forwarded-For"
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 核心逻辑：取逗号分隔的第一个 IP
            return forwarded.split(",")[0].trim();
        }
        // 2. 备选查 Nginx 常见头 "X-Real-IP"
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        // 3. 兜底：如果前面都没有，说明是直连，直接取连接 IP
        return request.getRemoteAddr();

    }

    /**
     * 校验 IP 是否有效
     */
    private static boolean isValid(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }
}
