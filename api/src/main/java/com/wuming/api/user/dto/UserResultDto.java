package com.wuming.api.user.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserResultDto implements Serializable {
    private boolean success;
    private String code;
    private String message;
    private UserDto user;

    @Serial
    private static final long serialVersionUID = 1L;

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
