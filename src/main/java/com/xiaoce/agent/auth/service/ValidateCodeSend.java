package com.xiaoce.agent.auth.service;
import com.xiaoce.agent.auth.domain.vo.SendCodeResponse;
import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;

/**
 * ValidateCodeSend
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/24 19:34
 */
public interface ValidateCodeSend {

    SendCodeResponse sendValidateCode(String email, ValidateCodeSendSceneEnums scene, String code , int expireTime);
}
