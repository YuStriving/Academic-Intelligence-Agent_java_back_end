package com.xiaoce.agent.auth.service.impl;

import com.xiaoce.agent.auth.common.utils.CollectionsUtils;
import com.xiaoce.agent.auth.domain.vo.ValidateCodeResult;
import com.xiaoce.agent.auth.enums.ValidateCodeSendSceneEnums;
import com.xiaoce.agent.auth.enums.VerificationCodeStatus;
import com.xiaoce.agent.auth.service.ValidateCodeStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static com.xiaoce.agent.auth.common.constant.AuthRedisConstants.*;

/**
 * ValidateCodeStoreImpl
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/24 19:48
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ValidateCodeStoreImpl implements ValidateCodeStore {
    private final StringRedisTemplate redisTemplate;
    @Override
    public void saveCode(ValidateCodeSendSceneEnums scene, String email, String code, Duration ttl, int maxAttempts) {
        String key = buildKeyAboutValidateCodeHash(email, scene);
        BoundHashOperations<String,String,String> ops = redisTemplate.boundHashOps(key);
        ops.put(VALIDATE_CODE, code);
        ops.put(ALREADY_ATTEMPT_COUNT, "0");
        ops.put(MAX_ATTEMPT_COUNT, String.valueOf(maxAttempts));
        ops.expire(ttl);
    }

    @Override
    public ValidateCodeResult verifyCode(ValidateCodeSendSceneEnums scene, String email, String code) {
        String key = buildKeyAboutValidateCodeHash(email, scene);
        BoundHashOperations<String,String,String> ops = redisTemplate.boundHashOps(key);
        Map<String, String> map = ops.entries();
        if (CollectionsUtils.isEmpty(map)){
            return new ValidateCodeResult(VerificationCodeStatus.EXPIRED,0,0);
        }
        // 获取hash中的值
        String maxAttempts = map.get(MAX_ATTEMPT_COUNT);
        String alreadyAttempts = map.get(ALREADY_ATTEMPT_COUNT);
        // 判断验证次数此时是否已经达到上限
        int max = parseInt(maxAttempts, 5);
        int already = parseInt(alreadyAttempts, 0);
        if (already >= max){
            return new ValidateCodeResult(VerificationCodeStatus.TOO_FREQUENT, already, max);
        }
        String validateCode = map.get(VALIDATE_CODE);
        // 判断验证码是否正确
        if (code.equals(validateCode)) {
            // 如果验证码正确
            // 删除redis中验证码的相关数据
            removeValidateCode(scene, email);
            return new ValidateCodeResult(VerificationCodeStatus.VALID, already, max);
        }
        // 验证码错误，则增加尝试次数并写回Redis
        already = already + 1;
        ops.put(ALREADY_ATTEMPT_COUNT, String.valueOf(already));

        //当用户连续输错验证码达到最大尝试次数（例如 5 次）后，代码会给该验证码对应的 Redis Key 设置一个 30 分钟的过期时间。这意味着：
        //30 分钟内，该手机号/邮箱的任何验证码尝试都会直接返回 TOO_FREQUENT（过于频繁），不能再继续尝试验证。
        //30 分钟后，Redis Key 自动删除，用户需要重新获取验证码，开始新的一轮验证。
        //这类似于账户临时锁定，但锁定对象是“验证码会话”，而不是整个用户账户。
        if (already > max) {
            ops.expire(Duration.ofMinutes(30));
            return new ValidateCodeResult(VerificationCodeStatus.TOO_FREQUENT, already, max);
        }
        return new ValidateCodeResult(VerificationCodeStatus.INVALID, already, max);
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null){
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        }catch (Exception e){
            log.error("转换失败",e);
            return defaultValue;
        }
    }

    /**
     * 删除验证码（清理验证码会话）
     * 
     * <p>使用场景：
     * <ol>
     *   <li>用户注册成功后，删除已使用的验证码，防止重复使用</li>
     *   <li>用户重新获取验证码时，先删除旧验证码再保存新验证码</li>
     *   <li>用户重置密码成功后，清理验证码残留数据</li>
     * </ol>
     * 
     * 
     * <p>与 verifyCode() 的配合：
     * verifyCode() 中验证码正确时也会调用 unlink(key)，
     * 本方法提供了业务层的显式删除入口，方便业务逻辑调用。
     * 
     * @param scene 验证码发送场景（如 REGISTER、RESETPASSWORD）
     * @param email 邮箱地址
     */
    @Override
    public void removeValidateCode(ValidateCodeSendSceneEnums scene, String email) {
        String key = buildKeyAboutValidateCodeHash(email, scene);
        redisTemplate.unlink(key);
        log.debug("验证码已删除 - 场景: {}, 邮箱: {}", scene, email);
    }
}
