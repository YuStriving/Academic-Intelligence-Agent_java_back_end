package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import com.xiaoce.agent.auth.config.AuthProperties;
import com.xiaoce.agent.auth.domain.vo.SendCodeResponse;
import com.xiaoce.agent.auth.domain.vo.ValidateCodeResult;
import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;
import com.xiaoce.agent.auth.service.ValidateCodeSend;
import com.xiaoce.agent.auth.service.ValidateCodeStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;

import static com.xiaoce.agent.auth.common.constant.AuthConstant.DAY_FORMAT;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.buildKeyAboutValidateLastCode;
import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.buildKeyAboutValidateSendCount;

/**
 * ValidateCodeImpl
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/24 20:27
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ValidateCodeImpl {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthProperties authProperties;
    private final StringRedisTemplate redisTemplate;
    private final ValidateCodeStore validateCodeStore;
    private final ValidateCodeSend validateCodeSend;

    public SendCodeResponse sendValidateCode(String email, ValidateCodeSendSceneEnums scene) {
        // 校验参数
        validateEmailAndScene(email, scene);
        // 校验配置是否存在
        if (authProperties.getVerification() == null) {
            throw new IllegalArgumentException("验证码配置不存在");
        }
        // 校验是否已经发送过验证码，并且是否达到发送间隔
        validateIsSendCode(email, scene, authProperties.getVerification().getSendInterval());
        // 校验验证码的发送次数是否达到最大的发送次数
        validateCodeIsExceedMaxCount(email, scene, authProperties.getVerification().getDailyLimit());
        //生成验证码
        String code = generateValidateCode(authProperties.getVerification().getCodeLength());
        // 将验证码存储到Redis当中
        validateCodeStore.saveCode(scene, email, code, authProperties.getVerification().getTtl(), authProperties.getVerification().getMaxAttempts());
        // TODO 发送验证码到控制台，实际调用短信方法可以发送验证码
        validateCodeSend.sendValidateCode(email, scene, code, (int) authProperties.getVerification().getTtl().toMillis());
        //返回结果
        return new SendCodeResponse(email, scene, (int) authProperties.getVerification().getTtl().toSeconds());
    }

    private String generateValidateCode(int codeLength) {
        // 创建一个指定长度的StringBuilder对象
        StringBuilder builder = new StringBuilder(codeLength);
        // 循环生成指定位数的随机数字
        for (int i = 0; i < codeLength; i++) {
            // 生成0-9的随机数字并追加到builder中
            builder.append(RANDOM.nextInt(10));
        }
        // 将StringBuilder转换为字符串并返回
        return builder.toString();
    }

    private void validateCodeIsExceedMaxCount(String email, ValidateCodeSendSceneEnums scene, int limit) {
        if (limit <= 0) return;
        LocalDate now = LocalDate.now();
        String date = DAY_FORMAT.format(now);
        String key = buildKeyAboutValidateSendCount(email, scene, date);
        Long count = redisTemplate.opsForValue().increment(key);
        // 如果计数器为null，则表示这是第一次发送验证码，设置过期时间为1天
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        // 如果计数器不为null且达到或超过限制次数，抛出业务异常
        if (count != null && count > limit) {  // 检查是否超过每日发送限制
            throw new BusinessException(ErrorCode.BAD_REQUEST, "今日发送次数已达到上限");  // 超过限制则抛出异常
        }
    }

    private void validateIsSendCode(String email, ValidateCodeSendSceneEnums scene, Duration sendInterval) {
        if (sendInterval == null || sendInterval.isNegative() || sendInterval.isZero()) {
            return;
        }
        String key = buildKeyAboutValidateLastCode(email, scene);
        // 判断一下当前的key是否存在，若是存在则没有达到发送间隔
        String hasValue = redisTemplate.opsForValue().get(key);
        // 如果有值则说明没达到发送间隔不能发送验证码
        if (StringUtils.hasText(hasValue)) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        }
        // 如果没有值则说明达到发送间隔，设置最后一次发送验证嘛的key
        redisTemplate.opsForValue().set(key, "1", sendInterval);
    }

    private void validateEmailAndScene(String email, ValidateCodeSendSceneEnums scene) {
        if (!StringUtils.hasText(email) || scene == null) {
            throw new IllegalArgumentException("邮箱和场景不能为空");
        }
    }

    public ValidateCodeResult verifyCode(String email, ValidateCodeSendSceneEnums scene, String code) {
        // 校验参数
        validateEmailAndScene(email, scene);
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        // 校验验证码
        return validateCodeStore.verifyCode(scene, email, code);
    }

/**
 * 使验证码失效的方法
 * @param email 用户邮箱地址，用于标识需要使验证码失效的用户
 * @param scene 验证码发送场景枚举，用于标识验证码的使用场景
 */
    public void inValidateCode(String email, ValidateCodeSendSceneEnums scene) {
    // 验证邮箱地址和发送场景的有效性
        validateEmailAndScene(email, scene);
    // 根据场景和邮箱从存储中移除对应的验证码，使其失效
        validateCodeStore.removeValidateCode(scene, email);
    }
}
