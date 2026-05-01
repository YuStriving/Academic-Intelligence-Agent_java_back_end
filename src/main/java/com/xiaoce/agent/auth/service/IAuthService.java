package com.xiaoce.agent.auth.service;

import com.xiaoce.agent.auth.domain.dto.*;
import com.xiaoce.agent.auth.domain.vo.AuthResponse;
import com.xiaoce.agent.auth.domain.vo.SendCodeResponse;
import com.xiaoce.agent.auth.domain.vo.TokenPairResponse;
import com.xiaoce.agent.auth.domain.vo.UserInfoResponse;
import com.xiaoce.agent.auth.enums.ClientType;
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

    AuthResponse login(LoginRequest req, ClientType clientType);

    AuthResponse register(RegisterRequest req ,ClientType clientType);

    void logout(String refreshToken,ClientType clientType);

    /**
     *  // 最简单的测试方式（不依赖脚本）
     *  fetch('/api/v1/auth/refresh', {
     *   method: 'POST',
     *   credentials: 'include',
     *   headers: {'Content-Type': 'application/json'},
     *   body: JSON.stringify({refreshToken: 'your-token-here'})
     * }).then(r => r.json()).then(console.log)
     * @param request
     * @param clientType
     * @return
     */
    TokenPairResponse refreshToken(@Valid RefreshRequest request,ClientType clientType);

    UserInfoResponse me(Long userId);

    void logoutAll(String refreshToken,ClientType clientType);
    // TODO 给管理端进行调用的
    void banUserPermanent(Long userId,ClientType clientType);


    void resetPassword(@Valid PasswordResetRequest request,ClientType clientType);

    SendCodeResponse sendValidateCode(@Valid SendCodeRequest request);

    void forgetPassword(@Valid ForgetPasswordRequest request,ClientType clientType);
}
