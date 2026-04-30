package com.xiaoce.agent.user.domain.dto;

import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;
import jakarta.validation.constraints.NotBlank;
import lombok.NonNull;

/**
 * UserDeleted
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/29 14:51
 */
public record UserDeletedRequest(
        @NotBlank(message = "用户名或邮箱不能为空")
        String emailOrUserName,
        @NotBlank(message = "验证码不能为空")
        String validateCode
) {
}
