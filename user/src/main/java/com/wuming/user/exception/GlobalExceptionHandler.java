package com.wuming.user.exception;

import com.wuming.user.domain.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求参数错误。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException exception) {
        return ApiResponse.error(400, exception.getMessage());
    }

    /**
     * 处理当前业务状态不允许继续执行的错误。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ApiResponse<Void> handleIllegalState(IllegalStateException exception) {
        return ApiResponse.error(409, exception.getMessage());
    }

    /**
     * 兜底处理未预期异常，避免内部堆栈暴露给调用方。
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("用户服务处理失败", exception);
        return ApiResponse.error(500, "用户服务处理失败");
    }
}
