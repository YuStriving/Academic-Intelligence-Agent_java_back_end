package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.domain.vo.SendCodeResponse;
import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;
import com.xiaoce.agent.auth.mapper.UsersMapper;
import com.xiaoce.agent.auth.service.ValidateCodeSend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * sendValidateCodeImpl
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/24 19:35
 */
@Service
@Slf4j
public class SendValidateCodeImpl implements ValidateCodeSend {

    @Override
    public SendCodeResponse sendValidateCode(String email, ValidateCodeSendSceneEnums scene, String code, int expireTime) {
        log.info("发送验证码到{}\n 验证码为{}\n过期时间为{}",email,code,expireTime);
        return null;
    }
}
