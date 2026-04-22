package com.xiaoce.agent.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.cors")
public class AppCorsProperties {
    private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";
}
