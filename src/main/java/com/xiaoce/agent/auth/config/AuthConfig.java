package com.xiaoce.agent.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.xiaoce.agent.auth.common.utils.PemUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, AppCorsProperties.class})
@RequiredArgsConstructor
public class AuthConfig {

    private final AuthProperties authProperties;


    /**
     * 配置密码加密器
     *
     * 作用：用于用户注册时加密密码，登录时验证密码
     *
     * 使用BCrypt算法：
     * - 安全性高：每次加密结果都不同，防止彩虹表攻击
     * - 强度可调：strength参数控制加密强度（4-31，推荐12）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        int strength = authProperties.getPassword().getBcryptStrength();
        return new BCryptPasswordEncoder(strength);
    }

    /**
     * 配置 JWT 编码器 (JwtEncoder)
     * 
     * 作用：负责生成JWT Token的核心组件
     * 实现：基于Nimbus库，使用RSA密钥对进行数字签名
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        AuthProperties.Jwt jwtProps = authProperties.getJwt();
        
        // 读取私钥（用于签名）
        RSAPrivateKey privateKey = PemUtils.readPrivateKey(jwtProps.getPrivateKey());
        
        // 读取公钥（用于验证）
        RSAPublicKey publicKey = PemUtils.readPublicKey(jwtProps.getPublicKey());
        
        // 构建JWK (JSON Web Key)对象
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(jwtProps.getKeyId())
                .build();
        
        // 创建JWKSource
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        
        // 生成最终的编码器
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * 配置 JWT 解码器 (JwtDecoder)
     * 
     * 作用：负责验证JWT Token的核心组件
     * 实现：基于Nimbus库，使用RSA公钥验证签名
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        AuthProperties.Jwt jwtProps = authProperties.getJwt();
        
        // 读取公钥（只需要公钥进行验证）
        RSAPublicKey publicKey = PemUtils.readPublicKey(jwtProps.getPublicKey());
        
        // 构建解码器
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}

