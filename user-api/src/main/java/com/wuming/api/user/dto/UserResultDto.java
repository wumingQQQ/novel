package com.wuming.api.user.dto;

import lombok.Data;

@Data
public class UserResultDto {
    private boolean success;
    private String code;
    private String message;
    private UserDto user;

    public static UserResultDto success(UserDto user) {
        UserResultDto result = new UserResultDto();
        result.setSuccess(true);
        result.setUser(user);
        return result;
    }

    public static UserResultDto failure(String code, String message) {
        UserResultDto result = new UserResultDto();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
