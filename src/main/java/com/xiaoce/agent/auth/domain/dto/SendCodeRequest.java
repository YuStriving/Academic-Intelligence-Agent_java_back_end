package com.xiaoce.agent.auth.domain.dto;

import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * SendCodeRequest
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/24 19:27
 */
public record SendCodeRequest(
        @NotBlank(message = "Username or email is required")
        String emailOrUsername,
        @NotNull(message = "Scene is required")
        ValidateCodeSendSceneEnums scene
) {
}
