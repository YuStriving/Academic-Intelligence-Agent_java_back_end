package com.xiaoce.agent.auth.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


public record RegisterRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        String email,

        @NotBlank(message = "Password is required")
        String password,

        @AssertTrue(message = "Please agree to terms")
        boolean agreeTerms
) {



}
