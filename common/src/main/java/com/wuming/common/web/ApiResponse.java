package com.wuming.common.web;

import com.wuming.common.exception.ErrorCode;

public record ApiResponse<T>(
        int code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<T>(200, "success", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<Void>(200, "success", null);
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<Void>(code, message, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<Void>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<Void>(errorCode.getCode(), message, null);
    }

}
