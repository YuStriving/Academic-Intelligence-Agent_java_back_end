package com.xiaoce.agent.user.controller;

import com.xiaoce.agent.auth.common.restapi.ApiResponse;
import com.xiaoce.agent.auth.context.ClientContextHolder;
import com.xiaoce.agent.auth.domain.dto.JwtUserInfo;
import com.xiaoce.agent.auth.enums.ClientType;
import com.xiaoce.agent.user.domain.dto.UserAIConfigRequest;
import com.xiaoce.agent.user.domain.dto.UserDeletedRequest;
import com.xiaoce.agent.user.domain.dto.UserProfileRequest;
import com.xiaoce.agent.user.domain.dto.UserRetrieveRequest;
import com.xiaoce.agent.user.domain.vo.UserAIConfigResponse;
import com.xiaoce.agent.user.domain.vo.UserProfileResponse;
import com.xiaoce.agent.user.domain.vo.UserRetrieveResponse;
import com.xiaoce.agent.user.service.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * UserController
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/25 20:12
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Valid
public class UserController {
        private final IUserService userService;

    @PatchMapping("/profile/update")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UserProfileRequest request,
                                                          @AuthenticationPrincipal JwtUserInfo userInfo) {
        log.info("更新用户资料请求");
        Long userId = userInfo.userId();
        return ApiResponse.ok(userService.updateProfile(userId,request));
    }

    @GetMapping("/profile/retrieve")
    public ApiResponse<UserRetrieveResponse> retrievePreferences(@AuthenticationPrincipal JwtUserInfo userInfo) {

        Long userId = userInfo.userId();
        log.info("获取用户{}检索偏好配置的请求",userId);
        return ApiResponse.ok(userService.retrieveProfile(userId));
    }

    @PatchMapping("/profile/retrieve/update")
    public ApiResponse<UserRetrieveResponse> updateRetrievePreferences(@Valid @RequestBody UserRetrieveRequest request, @AuthenticationPrincipal JwtUserInfo userInfo){
        Long userId = userInfo.userId();
        log.info("更新用户{}检索偏好配置的请求",userId);
        return ApiResponse.ok(userService.updateRetrievePreferences(userId,request));
    }

    @GetMapping("/profile/AIRAG")
    public ApiResponse<UserAIConfigResponse> getAiRAGConfig(@AuthenticationPrincipal JwtUserInfo userInfo){
        Long userId = userInfo.userId();
        log.info("获取用户{} -AI RAG配置的请求",userId);
        return ApiResponse.ok(userService.getAiRAGConfig(userId));
    }
    @PatchMapping("/profile/AIRAG/update")
    public ApiResponse<UserAIConfigResponse> updateAiRAGConfig(@Valid @RequestBody UserAIConfigRequest request,
                                                               @AuthenticationPrincipal JwtUserInfo userInfo){
        Long userId = userInfo.userId();
        log.info("更新用户{} -AI RAG配置的请求",userId);
        return ApiResponse.ok(userService.updateAiRAGConfig(userId,request));
    }

    @DeleteMapping("/profile/user/delete")
    public ApiResponse<Void> deleteUser(@AuthenticationPrincipal JwtUserInfo userInfo ,
                                        @RequestBody @Valid UserDeletedRequest request){
        Long userId = userInfo.userId();
        log.info("用户{}-注销的请求",userId);
        ClientType clientType = ClientContextHolder.get();
        userService.deleteUser(userId,request,clientType);
        return ApiResponse.ok();
    }

}
