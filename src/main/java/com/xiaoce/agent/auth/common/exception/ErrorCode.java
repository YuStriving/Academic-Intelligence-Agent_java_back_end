package com.xiaoce.agent.auth.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SUCCESS(200, "success", HttpStatus.OK),

    BAD_REQUEST(40001, "bad request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40101, "unauthorized", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, "token expired", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40301, "forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND(40401, "not found", HttpStatus.NOT_FOUND),
    USERNAME_EXISTS(40901, "username already exists", HttpStatus.CONFLICT),
    EMAIL_EXISTS(40902, "email already exists", HttpStatus.CONFLICT),

    INVALID_CREDENTIALS(40103, "invalid username or password", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(40104, "invalid refresh token", HttpStatus.UNAUTHORIZED),

    INTERNAL_ERROR(50001, "internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
