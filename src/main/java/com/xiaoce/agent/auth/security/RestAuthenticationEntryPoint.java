package com.xiaoce.agent.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.auth.common.restapi.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证失败统一处理器（401错误响应器）
 * 
 * <p>这个类是Spring Security的"未登录拦截统一处理器"。
 * 当用户认证失败时（未登录、token过期、token无效等），
 * Spring Security会自动调用此方法，返回统一的JSON格式错误响应。
 * 
 * <p>为什么需要这个类？
 * <pre>
 * 默认行为（不使用此类）：
 * - Spring Security会重定向到登录页面（适合传统Web应用）
 * - 或者直接返回403 Forbidden白页（不适合前后端分离）
 * 
 * 使用此类后：
 * - 返回标准JSON格式：{"code":401,"message":"Unauthorized"}
 * - 前端可以解析JSON并显示友好的错误提示
 * - 适合前后端分离的REST API架构
 * </pre>
 * 
 * <p>触发场景（什么时候会调用此方法）：
 * <ul>
 *   <li>用户未登录访问需要认证的接口</li>
 *   <li>JWT令牌过期（accessToken已失效）</li>
 *   <li>JWT令牌签名无效（被篡改或伪造）</li>
 *   <li>JWT令牌类型错误（用refreshToken访问接口）</li>
 *   <li>JWT令牌版本不匹配（用户已强制登出）</li>
 *   <li>用户被封禁（管理员在Redis中设置封禁标记）</li>
 * </ul>
 * 
 * <p>在Spring Security工作流程中的位置：
 * <pre>
 * 1. 用户发送请求，携带JWT令牌
 * 2. Spring Security验证JWT
 * 3. 验证失败，抛出AuthenticationException
 * 4. Spring Security捕获异常
 * 5. 调用此commence()方法
 * 6. 返回401 JSON响应给前端
 * </pre>
 * 
 * <p>响应格式示例：
 * <pre>
 * HTTP/1.1 401 Unauthorized
 * Content-Type: application/json;charset=UTF-8
 * 
 * {
 *   "code": 401,
 *   "message": "Unauthorized",
 *   "data": null
 * }
 * </pre>
 * 
 * <p>与其他组件的配合：
 * <ul>
 *   <li>SecurityConfig：配置exceptionHandling时注册此处理器</li>
 *   <li>AuthenticationException：Spring Security抛出的认证异常</li>
 *   <li>ApiResponse：统一的API响应格式</li>
 *   <li>ObjectMapper：将ApiResponse序列化为JSON</li>
 * </ul>
 * 
 * @author xiaoce
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * JSON序列化器（将ApiResponse对象转换为JSON字符串）
     */
    private final ObjectMapper objectMapper;

    /**
     * 处理认证失败（核心方法）
     * 
     * <p>当Spring Security检测到认证失败时，会自动调用此方法。
     * 此方法负责设置HTTP响应状态码和返回统一的JSON错误信息。
     * 
     * <p>处理流程：
     * <pre>
     * 1. 设置HTTP状态码为401 Unauthorized
     *    - 401表示"未授权"，需要重新认证
     *    - 与403 Forbidden不同（403表示"禁止访问"，即使认证通过也不允许）
     * 
     * 2. 设置响应字符编码为UTF-8
     *    - 确保中文等非ASCII字符正确显示
     * 
     * 3. 设置响应内容类型为application/json
     *    - 告知前端返回的是JSON格式
     * 
     * 4. 构建统一的错误响应体
     *    - 使用ApiResponse.fail()创建失败响应
     *    - 错误码：ErrorCode.UNAUTHORIZED
     *    - 数据：null（认证失败无数据返回）
     * 
     * 5. 将响应体序列化为JSON并写入响应流
     *    - 前端收到JSON后可解析并显示错误提示
     * </pre>
     * 
     * <p>前端处理建议：
     * <pre>
     * // 前端收到401响应后的处理逻辑
     * fetch('/api/v1/users/profile', {
     *   headers: { 'Authorization': 'Bearer ' + token }
     * }).then(response => {
     *   if (response.status === 401) {
     *     // 认证失败，跳转到登录页
     *     // 或者尝试刷新令牌（如果有refreshToken）
     *     window.location.href = '/login';
     *   }
     * });
     * </pre>
     * 
     * @param request HTTP请求对象（可用于获取请求路径等信息）
     * @param response HTTP响应对象（用于设置状态码和写入响应体）
     * @param authException 认证异常对象（包含失败原因）
     * @throws IOException 如果写入响应流失败
     * @throws ServletException 如果Servlet处理失败
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        // 步骤1：设置HTTP状态码为401 Unauthorized
        // 401表示"未授权"，客户端需要重新认证
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        // 步骤2：设置响应字符编码
        // 确保中文等非ASCII字符正确显示（如错误消息包含中文）
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        
        // 步骤3：设置响应内容类型
        // 告知前端返回的是JSON格式，字符集为UTF-8
        response.setContentType("application/json;charset=UTF-8");
        
        // 步骤4：构建统一的错误响应体
        // 使用ApiResponse.fail()创建标准失败响应
        // ErrorCode.UNAUTHORIZED包含错误码和错误消息
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.UNAUTHORIZED, null);
        
        // 步骤5：序列化并写入响应流
        // 将ApiResponse对象转换为JSON字符串，写入HTTP响应
        // 前端收到后可解析并显示错误提示
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
