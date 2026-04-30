package com.xiaoce.agent.auth.domain.vo;

import com.xiaoce.agent.auth.enums.VerificationCodeStatus;

/**
 * ValidateCodeResult
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/24 20:37
 */
public record ValidateCodeResult(
        VerificationCodeStatus status,
        int attemptCounts,
        int maxAttemptCounts
) {
    public boolean isSuccess() {
        return status == VerificationCodeStatus.VALID;
    }

}
