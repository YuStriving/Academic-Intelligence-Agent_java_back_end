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
        log.info("用户注册请求 - 用户名: {}, 邮箱: {}", req.username(), req.email());
        long startTime = System.currentTimeMillis();
        
        try {
            ApiResponse<AuthResponse> response = ApiResponse.ok(authService.register(req));
            long duration = System.currentTimeMillis() - startTime;
            log.info("用户注册成功 - 用户名: {}, 耗时: {}ms", req.username(), duration);
            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("用户注册失败 - 用户名: {}, 错误: {}, 耗时: {}ms", req.username(), e.getMessage(), duration, e);
            throw e;
        }
    }
    
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(@AuthenticationPrincipal JwtAuthenticationToken principal) {
        long userId = principal.getUserId();
        log.info("获取用户信息请求 - 用户ID: {}", userId);
        long startTime = System.currentTimeMillis();
        
        try {
            ApiResponse<UserInfoResponse> response = ApiResponse.ok(authService.me(userId));
            long duration = System.currentTimeMillis() - startTime;
            log.info("获取用户信息成功 - 用户ID: {}, 耗时: {}ms", userId, duration);
            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("获取用户信息失败 - 用户ID: {}, 错误: {}, 耗时: {}ms", userId, e.getMessage(), duration, e);
            throw e;
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("用户登录请求 - 邮箱/用户名: {}", req.emailOrUsername());
        long startTime = System.currentTimeMillis();
        
        try {
            ApiResponse<AuthResponse> response = ApiResponse.ok(authService.login(req));
            long duration = System.currentTimeMillis() - startTime;
            log.info("用户登录成功 - 邮箱/用户名: {}, 耗时: {}ms", req.emailOrUsername(), duration);
            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("用户登录失败 - 邮箱/用户名: {}, 错误: {}, 耗时: {}ms", req.emailOrUsername(), e.getMessage(), duration, e);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        log.info("令牌刷新请求 - 刷新令牌: {}...", req.refreshToken().substring(0, Math.min(20, req.refreshToken().length())));
        long startTime = System.currentTimeMillis();
        
        try {
            ApiResponse<TokenPairResponse> response = ApiResponse.ok(authService.refreshToken(req));
            long duration = System.currentTimeMillis() - startTime;
            log.info("令牌刷新成功 - 耗时: {}ms", duration);
            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("令牌刷新失败 - 错误: {}, 耗时: {}ms", e.getMessage(), duration, e);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        log.info("用户登出请求 - 刷新令牌: {}...", request.refreshToken().substring(0, Math.min(20, request.refreshToken().length())));
        long startTime = System.currentTimeMillis();
        
        try {
            authService.logout(request.refreshToken());
            long duration = System.currentTimeMillis() - startTime;
            log.info("用户登出成功 - 耗时: {}ms", duration);
            return ApiResponse.ok();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("用户登出失败 - 错误: {}, 耗时: {}ms", e.getMessage(), duration, e);
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
