package com.xiaoce.agent.auth.filter;

import com.xiaoce.agent.auth.context.ClientContextHolder;
import com.xiaoce.agent.auth.enums.ClientType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 客户端类型过滤器
 *
 * <p>从 HTTP 请求头中提取 {@code X-Client-Type}，解析并存储到 ThreadLocal 中，
 * 供后续的 Controller、Service 等层使用。
 *
 * <p>此 Filter 集成到 Spring Security 过滤器链中，在认证之前执行。
 *
 * <p>处理流程：
 * <ol>
 *   <li>从请求头读取 {@code X-Client-Type}</li>
 *   <li>验证合法性（白名单校验）</li>
 *   <li>存储到 {@link ClientContextHolder} (ThreadLocal)</li>
 *   <li>请求完成后清理 ThreadLocal（防止内存泄漏）</li>
 * </ol>
 *
 * @author 小策
 * @date 2026/4/27
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // 尽早执行（在 Security 过滤器之前）
public class ClientTypeFilter implements Filter {

    /** HTTP Header 名称：客户端类型 */
    public static final String HEADER_CLIENT_TYPE = "X-Client-Type";

    /** HTTP Header 名称：客户端版本号（可选） */
    public static final String HEADER_CLIENT_VERSION = "X-Client-Version";

    /** 默认客户端类型（当 Header 缺失或非法时使用） */
    private static final ClientType DEFAULT_CLIENT_TYPE = ClientType.WEB;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 从请求头获取客户端类型
            String clientTypeHeader = httpRequest.getHeader(HEADER_CLIENT_TYPE);
            String clientVersion = httpRequest.getHeader(HEADER_CLIENT_VERSION);
            // 解析并验证客户端类型
            ClientType clientType = parseAndValidateClientType(clientTypeHeader);
            // 存储到 ThreadLocal
            ClientContextHolder.set(clientType);
            // 获取请求的ip地址
            //将请求地址放入ThreadLocal当中
            ClientContextHolder.setIp(httpRequest.getRemoteAddr());
            // 记录日志（包含客户端版本信息）
            if (log.isDebugEnabled()) {
                log.debug("客户端类型识别成功 - 类型: {}, 版本: {}, URI: {}",
                    clientType.getCode(),
                    clientVersion != null ? clientVersion : "未知",
                    httpRequest.getRequestURI()
                );
            }

            // 继续执行后续过滤器链
            chain.doFilter(request, response);

        } catch (Exception e) {
            log.error("客户端类型识别失败，使用默认值 - 错误: {}", e.getMessage());
            // 出错时使用默认值，不阻塞请求
            ClientContextHolder.set(DEFAULT_CLIENT_TYPE);
            chain.doFilter(request, response);
        } finally {
            // ✅ 无论成功失败，都清理 ThreadLocal（防止内存泄漏）
            try {
                ClientContextHolder.clear();

                if (log.isTraceEnabled()) {
                    log.trace("ThreadLocal 已清理 - URI: {}", httpRequest.getRequestURI());
                }
            } catch (Exception e) {
                log.warn("清理 ThreadLocal 时发生异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 解析并验证客户端类型
     *
     * @param headerValue 请求头的值
     * @return 合法的客户端类型枚举，如果非法则返回默认值
     */
    private ClientType parseAndValidateClientType(@Nullable String headerValue) {
        // 情况1: Header 为空或空白
        if (headerValue == null || headerValue.isBlank()) {
            log.debug("未检测到 X-Client-Type Header，使用默认值: {}", DEFAULT_CLIENT_TYPE.getCode());
            return DEFAULT_CLIENT_TYPE;
        }

        // 情况2: 解析枚举值
        ClientType clientType = ClientType.fromCode(headerValue.trim());

        // 情况3: 非法值
        if (clientType == null) {
            log.warn("非法的 X-Client-Type 值: '{}', 使用默认值: {}", headerValue, DEFAULT_CLIENT_TYPE.getCode());
            return DEFAULT_CLIENT_TYPE;
        }

        return clientType;
    }
}
