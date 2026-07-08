package com.wuming.api.user.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserDto implements Serializable {
    private Long id;
    private String username;
    private String nickname;
    private String status;
    @Serial
    private static final long serialVersionUID = 1L;
}
