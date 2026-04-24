package com.xiaoce.agent.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Verification verification = new Verification();
    private final Password password = new Password();

    @Data
    public static class Jwt {
        private String issuer = "academic-agent";
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        private Duration refreshTokenTtl = Duration.ofDays(7);
        private String keyId = "academic-agent";
        private String privateKey = "classpath:keys/rsa-private.pem";
        private String publicKey = "classpath:keys/rsa-public.pem";
    }

    @Data
    public static class Verification {
        private int codeLength = 6;
        private Duration ttl = Duration.ofMinutes(5);
        private int maxAttempts = 5;
        private Duration sendInterval = Duration.ofSeconds(60);
        private int dailyLimit = 10;
    }

    @Data
    public static class Password {
        private int bcryptStrength = 12;
        private int minLength = 8;
        private int maxLength = 12;
    }
}
