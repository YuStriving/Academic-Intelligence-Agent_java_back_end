package com.xiaoce.agent.auth.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 64, message = "Username length must be 3-64")
        String username,
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 128, message = "Email is too long")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 64, message = "Password length must be 6-64")
        String password,

        @AssertTrue(message = "Please agree to terms")
        boolean agreeTerms
) {



}
