package com.glimmer.config.exception;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器（BE-05，见开发文档 §4.2 和 §4.16）
 * 统一转换为 { code, message, data } 响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 数据库唯一约束冲突（用户名重复、重复签到等）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        log.warn("唯一约束冲突: {}", message);
        if (message.contains("uk_username") || message.contains("username")) {
            return Result.error(ErrorCode.USERNAME_EXISTS);
        }
        if (message.contains("uk_user_date") || message.contains("sign_date")) {
            return Result.error(ErrorCode.ALREADY_SIGNED_IN);
        }
        if (message.contains("uk_bottle_user")) {
            return Result.error(ErrorCode.ALREADY_PICKED_BOTTLE);
        }
        if (message.contains("uk_reporter_target")) {
            return Result.error(ErrorCode.ALREADY_REPORTED);
        }
        if (message.contains("uk_campfire_user")) {
            return Result.error(ErrorCode.CONFLICT, "已加入该篝火");
        }
        return Result.error(ErrorCode.CONFLICT, "操作冲突，请勿重复提交");
    }

    /**
     * 请求体校验失败（@Valid @RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数错误";
        log.warn("参数校验失败: {}", message);
        return Result.error(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 表单参数绑定失败
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数错误";
        return Result.error(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 约束校验失败（@PathVariable @Min 等）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("参数错误");
        return Result.error(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 请求体不可读（JSON 格式错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return Result.error(ErrorCode.PARAM_ERROR, "请求体格式错误");
    }

    /**
     * 缺少请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(ErrorCode.PARAM_ERROR, "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(ErrorCode.PARAM_ERROR, "参数类型错误: " + e.getName());
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return Result.error(ErrorCode.INTERNAL_ERROR);
    }
}
