package com.wuming.user.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户注册请求
 */
@Data
public class RegisterRequest {
    @NotBlank
    private String username;
    private String nickname;
    @Email
    @NotBlank
    private String email;
    @NotBlank
    private String password;
}
