package com.wuming.novel.domain.dto;


import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ApiResonse<T> {
    private int code;
    private T data;
    private String message;

    public static <T> ApiResonse<T> success(T data) {
        return new ApiResonse<T>()
                .setCode(200)
                .setData(data)
                .setMessage(null);
    }

    public static <T> ApiResonse<T> error(String message) {
        return error(500, message);
    }
    public static <T> ApiResonse<T> error(int code, String message) {
        return new ApiResonse<T>()
                .setCode(code)
                .setData(null)
                .setMessage(message);
    }

    public boolean isSuccess(){
        return this.code == 200;
    }
}
