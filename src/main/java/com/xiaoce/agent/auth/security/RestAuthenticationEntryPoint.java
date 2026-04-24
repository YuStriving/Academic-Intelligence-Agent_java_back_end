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
 * 这个类是 Spring Security 的「未登录拦截统一处理器」
 * 作用：当用户没登录、登录过期、token 无效时，
 * 自动捕获异常，返回一段标准 JSON 格式的错误信息，而不是跳转到登录页或返回 403 白页。
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.UNAUTHORIZED, null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
