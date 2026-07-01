package com.wuming.common.web;

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

}
