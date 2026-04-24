package com.xiaoce.agent.auth.controller;

import com.xiaoce.agent.auth.domain.dto.LoginRequest;
import com.xiaoce.agent.auth.domain.dto.LogoutRequest;
import com.xiaoce.agent.auth.domain.dto.RefreshRequest;
import com.xiaoce.agent.auth.domain.dto.RegisterRequest;
import com.xiaoce.agent.auth.security.JwtAuthenticationToken;
import com.xiaoce.agent.auth.service.IAuthService;
import com.xiaoce.agent.auth.domain.vo.AuthResponse;
import com.xiaoce.agent.auth.domain.vo.TokenPairResponse;
import com.xiaoce.agent.auth.domain.vo.UserInfoResponse;
import com.xiaoce.agent.auth.common.restapi.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        try {
            ApiResponse<AuthResponse> response = ApiResponse.ok(authService.register(req));
            log.info("用户注册成功 - 用户名: {}", req.username());
            return response;
        } catch (Exception e) {
            log.error("用户注册失败 - 用户名: {}, 错误: {}", req.username(), e.getMessage());
            throw e;
        }
    }
    
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(@AuthenticationPrincipal JwtAuthenticationToken principal) {
        long userId = principal.getUserId();
        try {
            ApiResponse<UserInfoResponse> response = ApiResponse.ok(authService.me(userId));
            log.info("获取用户信息成功 - 用户ID: {}", userId);
            return response;
        } catch (Exception e) {
            log.error("获取用户信息失败 - 用户ID: {}, 错误: {}", userId, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        try {
            ApiResponse<AuthResponse> response = ApiResponse.ok(authService.login(req));
            log.info("用户登录成功 - 用户: {}", req.emailOrUsername());
            return response;
        } catch (Exception e) {
            log.error("用户登录失败 - 用户: {}, 错误: {}", req.emailOrUsername(), e.getMessage());
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        try {
            ApiResponse<TokenPairResponse> response = ApiResponse.ok(authService.refreshToken(req));
            log.info("令牌刷新成功");
            return response;
        } catch (Exception e) {
            log.error("令牌刷新失败 - 错误: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        try {
            authService.logout(request.refreshToken());
            log.info("用户登出成功");
            return ApiResponse.ok();
        } catch (Exception e) {
            log.error("用户登出失败 - 错误: {}", e.getMessage());
            throw e;
        }
    }

    @PostMapping("/logout/all")
    public ApiResponse<Void> logoutAll(@Valid @RequestBody LogoutRequest request) {
        log.info("用户全设备下线请求");
        authService.logoutAll(request.refreshToken());
        return ApiResponse.ok();
    }
}
