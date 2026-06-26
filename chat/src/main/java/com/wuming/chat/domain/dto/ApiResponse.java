package com.wuming.chat.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ApiResponse<T> {
    private int code;
    private T data;
    private String message;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<T>()
                .setCode(200)
                .setData(data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<T>()
                .setCode(code)
                .setMessage(message);
    }
}
