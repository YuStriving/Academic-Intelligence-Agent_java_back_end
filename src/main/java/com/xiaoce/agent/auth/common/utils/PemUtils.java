package com.xiaoce.agent.auth.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Scanner;

/**
 * PEM 格式密钥读取工具类
 * <p>
 * 用于从 PEM 格式文件中读取 RSA 公钥和私钥
 */
public class PemUtils {
    public PemUtils(){

    }

    /**
     * 从 PEM 文件读取 RSA 私钥
     *
     * @param filename PEM 文件路径（支持classpath:前缀）
     * @return RSA 私钥对象
     */
    public static RSAPrivateKey readPrivateKey(String filename) {
        try {
            String key = readKeyContent(filename);
            
            String privateKeyPEM = key
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            
            byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
            
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read private key from: " + filename, e);
        }
    }

    /**
     * 从 PEM 文件读取 RSA 公钥
     *
     * @param filename PEM 文件路径（支持classpath:前缀）
     * @return RSA 公钥对象
     */
    public static RSAPublicKey readPublicKey(String filename) {
        try {
            String key = readKeyContent(filename);
            
            String publicKeyPEM = key
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            
            byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
            
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read public key from: " + filename, e);
        }
    }

    /**
     * 读取密钥文件内容，支持classpath路径
     *
     * @param filename 文件路径
     * @return 文件内容
     */
    private static String readKeyContent(String filename) {
        try {
            if (filename.startsWith("classpath:")) {
                // 处理classpath路径
                String resourcePath = filename.substring("classpath:".length());
                InputStream inputStream = PemUtils.class.getClassLoader().getResourceAsStream(resourcePath);
                if (inputStream == null) {
                    throw new IOException("Resource not found: " + resourcePath);
                }
                Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name());
                String content = scanner.useDelimiter("\\A").next();
                scanner.close();
                return content;
            } else {
                // 处理文件系统路径
                return new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read key content from: " + filename, e);
        }
    }
}