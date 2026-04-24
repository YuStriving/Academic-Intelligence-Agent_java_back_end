package com.xiaoce.agent.auth.common.exception;

import com.xiaoce.agent.auth.common.restapi.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 
 * <p>统一捕获和处理应用中的各类异常，将它们转换为标准化的API响应格式。
 * 保证前端总是收到一致的JSON响应结构，便于错误处理和用户提示。
 * 
 * <p>处理的异常类型：
 * <ul>
 *   <li>业务异常：自定义业务逻辑异常</li>
 *   <li>参数验证异常：@Valid注解验证失败</li>
 *   <li>约束验证异常：数据库约束违反</li>
 *   <li>访问拒绝异常：权限不足</li>
 *   <li>其他异常：兜底处理所有未捕获的异常</li>
 * </ul>
 * 
 * <p>使用场景：
 * <ul>
 *   <li>所有Controller抛出的异常都会被此类捕获处理</li>
 *   <li>统一错误响应格式，便于前端处理</li>
 *   <li>记录错误日志，便于问题排查</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     * 
     * <p>捕获自定义业务异常，根据错误码返回相应的HTTP状态和错误信息。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>用户名已存在</li>
     *   <li>密码错误</li>
     *   <li>用户状态被禁用</li>
     *   <li>令牌无效或过期</li>
     * </ul>
     * 
     * @param e 业务异常
     * @return 统一格式的错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        log.debug("业务异常 - 错误码: {}, 消息: {}", ec.getCode(), e.getMessage());
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiResponse.fail(ec, e.getMessage()));
    }

    /**
     * 处理参数验证异常
     * 
     * <p>捕获@Valid注解验证失败的异常，提取第一个字段错误信息返回。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>注册时用户名格式不正确</li>
     *   <li>密码长度不符合要求</li>
     *   <li>邮箱格式错误</li>
     * </ul>
     * 
     * @param e 参数验证异常
     * @return 统一格式的错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        log.debug("参数验证异常 - 消息: {}", msg);
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, msg));
    }

    /**
     * 处理约束验证异常
     * 
     * <p>捕获@Validated等注解的约束验证异常。
     * 
     * @param e 约束验证异常
     * @return 统一格式的错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e) {
        log.debug("约束验证异常 - 消息: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, e.getMessage()));
    }

    /**
     * 处理访问拒绝异常
     * 
     * <p>捕获Spring Security权限不足的异常，返回403禁止访问状态码。
     * 
     * <p>使用场景：
     * <ul>
     *   <li>普通用户访问管理员接口</li>
     *   <li>权限配置不匹配</li>
     * </ul>
     * 
     * @param e 访问拒绝异常
     * @return 统一格式的错误响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException e) {
        log.warn("访问拒绝异常 - 消息: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN, null));
    }

    /**
     * 兜底处理所有未捕获的异常
     * 
     * <p>捕获所有其他未处理的异常，记录详细日志并返回500内部服务器错误。
     * 
     * <p>重要：此方法会记录完整的堆栈信息，便于问题排查，
     * 但返回给前端的信息会简化，避免暴露系统内部细节。
     * 
     * @param e 未捕获的异常
     * @return 统一格式的错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAny(Exception e) {
        log.error("未处理的异常", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, null));
    }
}
