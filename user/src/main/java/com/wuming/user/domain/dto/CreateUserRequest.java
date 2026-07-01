package com.wuming.user.domain.dto;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String username;
    private String nickname;
    private String password;
    private String email;
}
