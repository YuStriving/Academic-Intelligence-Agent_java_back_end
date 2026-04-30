package com.xiaoce.agent.auth.domain.vo;

import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * SendCodeResponse
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/24 19:43
 */
public record SendCodeResponse(
        @NotBlank(message = "Email or username is required")
        String emailOrUsername,
        @NotNull(message = "Scene is required")
        ValidateCodeSendSceneEnums scene,
        @NotNull(message = "expireTime is required")
        Integer expireTime
) {
}
