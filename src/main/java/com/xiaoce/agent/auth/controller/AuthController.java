package com.xiaoce.agent.auth.controller;
import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.auth.context.ClientContextHolder;
import com.xiaoce.agent.auth.domain.dto.*;
import com.xiaoce.agent.auth.domain.vo.*;
import com.xiaoce.agent.auth.enums.ClientType;
import com.xiaoce.agent.auth.service.IAuthService;
import com.xiaoce.agent.auth.common.restapi.ApiResponse;
import com.xiaoce.agent.auth.service.impl.JwtTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import com.xiaoce.agent.auth.domain.dto.RefreshRequest;
import com.xiaoce.agent.auth.domain.dto.LogoutRequest;
import com.xiaoce.agent.auth.domain.dto.PasswordResetRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 认证接口控制器
 * 
 * <p>提供用户认证相关的HTTP接口，包括用户注册、登录、登出、获取用户信息和刷新令牌等功能。
 * 所有接口统一返回ApiResponse格式的数据，便于前端统一处理。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>用户注册：通过/register接口创建新账户</li>
 *   <li>用户登录：通过/login接口验证用户身份并获取JWT令牌</li>
 *   <li>获取用户信息：通过/me接口获取当前登录用户的信息</li>
 *   <li>刷新令牌：通过/refresh接口在访问令牌过期前获取新的令牌</li>
 *   <li>用户登出：通过/logout和/logout/all接口使令牌失效</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Valid
//TODO 目前只实现了单个网页端登录，从而导致刷新令牌每登录一次就生成一个令牌，导致令牌堆积后续完善
public class AuthController {

    private final IAuthService authService;
    private final JwtTokenService jwtTokenService;

    /**
     * 用户注册接口
     * 
     * <p>创建新用户账户，系统会自动生成学术ID、昵称等信息，并分配初始角色。
     * 注册成功后会立即生成JWT令牌对，用户可以直接使用这些令牌访问受保护的接口。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>新用户首次注册账户</li>
     *   <li>通过邀请链接注册</li>
     * </ul>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>注册成功后，前端应保存返回的accessToken和refreshToken</li>
     *   <li>用户可以使用返回的令牌立即访问其他需要登录的接口</li>
     *   <li>后续可以通过refresh接口刷新过期的令牌</li>
     * </ul>
     * 
     * @param req 注册请求，包含用户名、邮箱、密码和同意条款状态
     * @return 包含用户信息和JWT令牌对的响应
     */
    @PostMapping("/register")
    public ApiResponse<AuthNoRefreshTokenResponse> register(@Valid @RequestBody RegisterRequest req,
                                                            HttpServletResponse response) {
        try {
            // 调用服务层完成用户注册逻辑
            // 服务层会验证输入、检查用户名/邮箱唯一性、加密密码、保存到数据库
            // 并生成JWT令牌对，返回给前端

            // 获取当前登录的客户端的信息
            ClientType clientType = ClientContextHolder.get();
            log.info("用户注册, 客户端类型: {}", clientType.getDescription());
            AuthResponse authResponse = authService.register(req,clientType );
            log.info("用户注册成功 - 用户名: {}", req.username());
            jwtTokenService.setRefreshTokenCookie(response,authResponse.token().refreshToken(),authResponse.token().expiresIn());
            AuthNoRefreshTokenResponse noRefreshTokenResponse = AuthNoRefreshTokenResponse.toAuthNoRefreshTokenResponse(authResponse);
            return ApiResponse.ok(noRefreshTokenResponse);
        } catch (Exception e) {
            log.error("用户注册失败 - 用户名: {}, 错误: {}", req.username(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 获取当前登录用户信息接口
     * 
     * <p>根据JWT令牌中的用户ID查询并返回用户的详细信息。该接口需要在请求头中携带有效的访问令牌。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户登录后获取个人信息展示在首页或个人中心</li>
     *   <li>编辑个人资料前先获取当前信息</li>
     * </ul>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>需要先调用login或register接口获取有效的accessToken</li>
     *   <li>accessToken过期后，可以通过refresh接口刷新后再调用此接口</li>
     * </ul>
     * 
     * @param userInfo 当前认证用户的JWT令牌信息
     * @return 用户详细信息响应
     */
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(@AuthenticationPrincipal JwtUserInfo userInfo) {
        // 防御性检查：如果用户信息为空，说明认证异常，返回401错误
        if (userInfo == null) {
            throw new com.xiaoce.agent.auth.common.exception.BusinessException(
                com.xiaoce.agent.auth.common.exception.ErrorCode.UNAUTHORIZED, 
                "User authentication information is missing"
            );
        }
        
        long userId = userInfo.userId();
        try {
            // 从JWT令牌中获取用户ID，然后查询数据库获取用户详细信息
            // 返回的用户信息包括：昵称、头像、学术ID、邮箱等
            UserInfoResponse response = authService.me(userId);
            log.info("获取用户信息成功 - 用户ID: {}", userId);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("获取用户信息失败 - 用户ID: {}, 错误: {}", userId, e.getMessage());
            throw e;
        }
    }

    /**
     * 用户登录接口
     * 
     * <p>验证用户身份（邮箱或用户名 + 密码），验证成功后生成JWT令牌对并保存刷新令牌到Redis。
     * 如果用户状态被禁用，会拒绝登录。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户通过邮箱登录</li>
     *   <li>用户通过用户名登录</li>
     * </ul>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>登录成功后，前端应保存返回的accessToken和refreshToken</li>
     *   <li>accessToken过期时，使用refreshToken调用refresh接口刷新</li>
     *   <li>用户登出时，调用logout接口使当前令牌失效</li>
     * </ul>
     * 
     * @param req 登录请求，包含邮箱/用户名和密码
     * @return 包含用户信息和JWT令牌对的响应
     */
    @PostMapping("/login")
    public ApiResponse<AuthNoRefreshTokenResponse> login(@Valid @RequestBody LoginRequest req,
                                                         HttpServletResponse response) {
        try {
            // 调用服务层完成用户登录逻辑
            // 服务层会验证用户身份、检查密码、生成JWT令牌对并保存到Redis
            ClientType clientType = ClientContextHolder.get();
            log.info("用户登录, 客户端类型: {}", clientType.getDescription());
            AuthResponse authResponse = authService.login(req,clientType);
            log.info("用户登录成功 - 用户: {}", req.emailOrUsername());
            // 将新的刷新令牌设置到HttpOnly Cookie中（替换旧的）
            jwtTokenService.setRefreshTokenCookie(response, authResponse.token().refreshToken(), authResponse.token().refreshExpiresIn());
            AuthNoRefreshTokenResponse noRefreshTokenResponse = AuthNoRefreshTokenResponse.toAuthNoRefreshTokenResponse(authResponse);
            return ApiResponse.ok(noRefreshTokenResponse);
        } catch (Exception e) {
            log.error("用户登录失败 - 用户: {}, 错误: {}", req.emailOrUsername(), e.getMessage());
            throw e;
        }
    }

    /**
     * 刷新访问令牌接口
     *
     * <p>使用有效的刷新令牌获取新的访问令牌和刷新令牌。原刷新令牌会被失效。
     * 该接口需要在访问令牌过期前调用，以维持用户的登录状态。
     *
     * <p>刷新令牌从HttpOnly Cookie中自动读取，无需前端手动传递。
     *
     * @param response HTTP响应对象（用于设置新的Cookie）
     * @return 新的JWT令牌对响应
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthAccessToken> refresh(HttpServletResponse response,
                                                HttpServletRequest request) {
        try {
            // 从HttpOnly Cookie中读取refreshToken（而不是从请求体）
            String refreshToken = null;
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("refreshToken".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                        break;
                    }
                }
            }
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "刷新令牌在cookie中未找到");
            }

            // 调用服务层完成令牌刷新逻辑
            ClientType clientType = ClientContextHolder.get();
            log.info("刷新令牌{}, 客户端类型: {}", refreshToken,clientType.getDescription());
            RefreshRequest refreshReq = new RefreshRequest(refreshToken);
            TokenPairResponse tokenPair = authService.refreshToken(refreshReq, clientType);
            log.info("令牌刷新成功");

            // 将新的刷新令牌设置到HttpOnly Cookie中（替换旧的）
            jwtTokenService.setRefreshTokenCookie(response, tokenPair.refreshToken(), tokenPair.refreshExpiresIn());
            AuthAccessToken authAccessToken = AuthAccessToken.toAuthAccessToken(tokenPair);
            return ApiResponse.ok(authAccessToken);
        } catch (Exception e) {
            log.error("令牌刷新失败 - 错误: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 用户登出接口
     * 
     * <p>使传入的刷新令牌失效，用户需要重新登录才能获取新的令牌。
     * 只影响当前设备的令牌，不影响其他设备。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户主动退出登录</li>
     *   <li>切换账户前先登出</li>
     * </ul>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>登出后，前端应清除本地保存的accessToken和refreshToken</li>
     *   <li>用户需要重新调用login接口才能获取新的令牌</li>
     *   <li>如果用户在多个设备登录，其他设备不受影响</li>
     * </ul>
     * 
     * @param request 登出请求，包含刷新令牌
     * @return 空的成功响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        try {
            // 调用服务层完成用户登出逻辑
            // 服务层会使当前refreshToken失效，用户需要重新登录
            ClientType clientType = ClientContextHolder.get();
            authService.logout(request.refreshToken(),clientType);
            log.info("用户登出成功");
            return ApiResponse.ok();
        } catch (Exception e) {
            log.error("用户登出失败 - 错误: {}", e.getMessage());
            throw e;
        }
    }


    /**
     * 重置密码接口
     * 
     * <p>用户通过邮箱或用户名、验证码和新密码来重置密码。
     * 重置成功后，所有设备上的令牌都会失效，用户需要重新登录。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户忘记密码时重置密码</li>
     *   <li>用户主动修改密码</li>
     * </ul>
     * 
     * <p>与其他方法的配合：
     * <ul>
     *   <li>重置密码后，用户需要重新调用login接口登录</li>
     *   <li>所有之前获取的令牌都会失效，其他设备也会被踢下线</li>
     * </ul>
     * 
     * @param request 重置密码请求，包含邮箱/用户名、验证码和新密码
     * @return 空的成功响应
     */
    @PostMapping("/reset/password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        log.info("更新密码请求");
        // 调用服务层完成密码重置逻辑
        // 服务层会验证验证码、更新密码、使所有令牌失效
        ClientType clientType = ClientContextHolder.get();
        authService.resetPassword(request,clientType);
        return ApiResponse.ok();
    }

    @PostMapping("/sendCode")
    public ApiResponse<SendCodeResponse> sendValidateCode(@Valid @RequestBody SendCodeRequest request) {
        log.info("发送验证码请求");
        // 调用服务层完成验证码发送逻辑
        // 服务层会验证邮箱/用户名、发送验证码到指定邮箱
        return ApiResponse.ok(authService.sendValidateCode(request));
    }

    @PostMapping("/forget/password")
    public ApiResponse<Void> forgetPassword(@Valid @RequestBody ForgetPasswordRequest request) {
        log.info("忘记密码请求");
        // 调用服务层完成忘记密码逻辑
        // 服务层会验证验证码、更新密码、使所有令牌失效
        ClientType clientType = ClientContextHolder.get();
        authService.forgetPassword(request,clientType);
        return ApiResponse.ok();
    }

}
