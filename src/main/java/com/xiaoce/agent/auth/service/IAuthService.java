package com.xiaoce.agent.auth.service;

import com.xiaoce.agent.auth.domain.dto.LoginRequest;
import com.xiaoce.agent.auth.domain.dto.RegisterRequest;
import com.xiaoce.agent.auth.domain.dto.TokenRefreshRequest;
import com.xiaoce.agent.auth.domain.vo.AuthResponse;
import com.xiaoce.agent.auth.domain.vo.TokenPairResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * IAuthService
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/22 11:57
 */
public interface IAuthService {

    AuthResponse login(LoginRequest req);

    AuthResponse register(RegisterRequest req);

    void logout(@NotBlank(message = "刷新令牌不能为空") String refreshToken);

    TokenPairResponse refreshToken(@Valid TokenRefreshRequest request);






}
