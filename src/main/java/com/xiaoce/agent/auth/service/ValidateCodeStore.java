package com.xiaoce.agent.auth.service;

import com.xiaoce.agent.auth.domain.vo.ValidateCodeResult;
import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;

import java.time.Duration;

/**
 * ValidateCodeSore
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/24 19:48
 */
public interface ValidateCodeStore {

    void saveCode(ValidateCodeSendSceneEnums scene, String email, String code, Duration ttl, int maxAttempts);

    ValidateCodeResult verifyCode(ValidateCodeSendSceneEnums scene, String email, String code);

    void  removeValidateCode(ValidateCodeSendSceneEnums scene, String email);
}
