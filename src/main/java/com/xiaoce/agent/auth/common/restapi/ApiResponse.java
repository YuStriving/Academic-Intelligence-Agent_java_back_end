package com.xiaoce.agent.auth.common.restapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Business code. 200 means success.
     */
    private int code = 200;

    private String message = "success";
    private T data;
    private long timestamp = System.currentTimeMillis();

    public ApiResponse(int code, String message, T data, long timestamp) {
        this.code = normalizeCode(code);
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public void setCode(int code) {
        this.code = normalizeCode(code);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data, System.currentTimeMillis());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(200, "success", null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> fail(ErrorCode ec, String overrideMessage) {
        String msg = overrideMessage != null && !overrideMessage.isBlank() ? overrideMessage : ec.getMessage();
        return new ApiResponse<>(ec.getCode(), msg, null, System.currentTimeMillis());
    }

    private static int normalizeCode(int code) {
        return code == 0 ? ErrorCode.SUCCESS.getCode() : code;
    }
}
