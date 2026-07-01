package com.wuming.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求
 */
@Data
public class LoginRequest {
    @NotBlank
    private String account;
    @NotBlank
    private String password;
}
