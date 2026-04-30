package com.xiaoce.agent.auth.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    SUCCESS(200, "成功", HttpStatus.OK),

    BAD_REQUEST(40001, "请求错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40101, "未登录", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, "令牌过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40301, "forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND(40401, "not found", HttpStatus.NOT_FOUND),
    USERNAME_EXISTS(40901, "用户已经存在", HttpStatus.CONFLICT),
    EMAIL_EXISTS(40902, "邮箱已经存在", HttpStatus.CONFLICT),

    INVALID_CREDENTIALS(40103, "用户名或者密码错误", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(40104, "非法的刷新令牌", HttpStatus.UNAUTHORIZED),

    INTERNAL_ERROR(50001, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
