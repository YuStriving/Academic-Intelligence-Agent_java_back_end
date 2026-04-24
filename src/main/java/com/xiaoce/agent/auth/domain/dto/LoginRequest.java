package com.xiaoce.agent.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


public record LoginRequest(
        @NotBlank(message = "Username or email is required")
        String emailOrUsername,

        @NotBlank(message = "Password is required")
        String password
) {


}
