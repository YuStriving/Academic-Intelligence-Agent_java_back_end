package com.xiaoce.agent.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username or email is required")
    private String emailOrUsername;

    @NotBlank(message = "Password is required")
    private String password;

}
