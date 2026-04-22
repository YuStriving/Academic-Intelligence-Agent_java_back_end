package com.xiaoce.agent.auth.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SUCCESS(200, "OK", HttpStatus.OK),

    BAD_REQUEST(40001, "Bad request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40101, "Unauthorized", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, "Token expired", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40301, "Forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND(40401, "Resource not found", HttpStatus.NOT_FOUND),
    USERNAME_EXISTS(40901, "Username already exists", HttpStatus.CONFLICT),
    EMAIL_EXISTS(40902, "Email already exists", HttpStatus.CONFLICT),

    INVALID_CREDENTIALS(40103, "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(40104, "Invalid or expired refresh token", HttpStatus.UNAUTHORIZED),

    INTERNAL_ERROR(50001, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
