package com.wuming.user.controller;

import com.wuming.api.user.dto.UserDto;
import com.wuming.user.domain.dto.ApiResponse;
import com.wuming.user.domain.dto.CreateUserRequest;
import com.wuming.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * 创建开发环境基础用户，后续接入登录注册后可替换为正式入口。
     */
    @PostMapping
    public ApiResponse<Long> createUser(@RequestBody CreateUserRequest request) {
        return ApiResponse.success(userService.createUser(request));
    }

    /**
     * 查询当前可用用户基础信息。
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserDto> getUser(@PathVariable Long userId) {
        return ApiResponse.success(userService.getRequiredUser(userId));
    }
}
