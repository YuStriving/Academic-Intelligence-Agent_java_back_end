package com.xiaoce.agent.user.utils;

import lombok.extern.slf4j.Slf4j;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 加密解密工具类
 * <p>
 * 用于敏感数据（如 API 密钥）的加密存储和解密读取
 * <p>
 * 使用说明：
 * <pre>
 * 1. 加密存储：AesEncryptUtil.encrypt(plainText, secretKey)
 * 2. 解密使用：AesEncryptUtil.decrypt(encryptedText, secretKey)
 *
 * 配置方式：
 * 在 application.yml 中配置 encryption.secret-key（必须为16/24/32位长度）
 * </pre>
 *
 * @author 小策
 * @since 2026-04-28
 */
@Slf4j
public final class AesEncryptUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String IV = "0123456789abcdef"; // 固定IV（生产环境建议动态生成）

    public AesEncryptUtil() {
    }

    /**
     * AES 加密
     *
     * @param plainText 明文
     * @param secretKey 密钥（必须为16/24/32字节）
     * @return Base64编码的密文
     */
    public static String encrypt(String plainText, String secretKey) {
        try {
            if (plainText == null || plainText.isEmpty()) {
                return plainText;
            }

            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(
                    IV.getBytes(StandardCharsets.UTF_8));

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] encrypted = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            log.error("AES加密失败", e);
            throw new RuntimeException("加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * AES 解密
     *
     * @param encryptedText Base64编码的密文
     * @param secretKey     密钥（必须与加密时一致）
     * @return 明文
     */
    public static String decrypt(String encryptedText, String secretKey) {
        try {
            if (encryptedText == null || encryptedText.isEmpty()) {
                return encryptedText;
            }

            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(
                    IV.getBytes(StandardCharsets.UTF_8));

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decrypted = cipher.doFinal(
                    Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("AES解密失败", e);
            throw new RuntimeException("解密失败: " + e.getMessage(), e);
        }
    }
}
