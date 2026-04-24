package com.xiaoce.agent.auth.service;

import com.xiaoce.agent.auth.domain.dto.LoginRequest;
import com.xiaoce.agent.auth.domain.dto.RefreshRequest;
import com.xiaoce.agent.auth.domain.dto.RegisterRequest;
import com.xiaoce.agent.auth.domain.vo.AuthResponse;
import com.xiaoce.agent.auth.domain.vo.TokenPairResponse;
import com.xiaoce.agent.auth.domain.vo.UserInfoResponse;
import jakarta.validation.Valid;

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

    void logout(String refreshToken);

    TokenPairResponse refreshToken(@Valid RefreshRequest request);

    UserInfoResponse me(Long userId);

    void logoutAll(String refreshToken);
    // TODO 永久封禁用户
    void banUserPermanent(Long userId);


}
