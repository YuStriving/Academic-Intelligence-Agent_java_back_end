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
public class AuthController {

    private final IAuthService authService;

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
     * @param req 注册请求，包含用户名、邮箱、密码和同意条款状态
     * @return 包含用户信息和JWT令牌对的响应
     */
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        try {
            AuthResponse response = authService.register(req);
            log.info("用户注册成功 - 用户名: {}", req.username());
            return ApiResponse.ok(response);
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
     * @param principal 当前认证用户的JWT令牌信息
     * @return 用户详细信息响应
     */
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(@AuthenticationPrincipal JwtAuthenticationToken principal) {
        long userId = principal.getUserId();
        try {
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
     * @param req 登录请求，包含邮箱/用户名和密码
     * @return 包含用户信息和JWT令牌对的响应
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        try {
            AuthResponse response = authService.login(req);
            log.info("用户登录成功 - 用户: {}", req.emailOrUsername());
            return ApiResponse.ok(response);
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
     * <p>使用场景：
     * <ul>
     *   <li>访问令牌即将过期，前端自动调用刷新</li>
     *   <li>用户重新打开应用需要续期</li>
     * </ul>
     * 
     * @param req 刷新令牌请求，包含刷新令牌
     * @return 新的JWT令牌对响应
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        try {
            TokenPairResponse response = authService.refreshToken(req);
            log.info("令牌刷新成功");
            return ApiResponse.ok(response);
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
     * @param request 登出请求，包含刷新令牌
     * @return 空的成功响应
     */
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

    /**
     * 用户全设备登出接口
     * 
     * <p>使该用户所有设备的刷新令牌失效，增加令牌版本号，所有旧令牌都将失效。
     * 用户需要在所有设备上重新登录。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>怀疑账户被盗用，强制所有设备重新登录</li>
     *   <li>修改密码后，希望旧令牌全部失效</li>
     * </ul>
     * 
     * @param request 登出请求，包含刷新令牌
     * @return 空的成功响应
     */
    @PostMapping("/logout/all")
    public ApiResponse<Void> logoutAll(@Valid @RequestBody LogoutRequest request) {
        log.info("用户全设备下线请求");
        authService.logoutAll(request.refreshToken());
        return ApiResponse.ok();
    }
}
