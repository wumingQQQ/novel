package com.wuming.user.controller;

import com.wuming.api.user.dto.UserDto;
import com.wuming.common.web.ApiResponse;
import com.wuming.user.domain.dto.CreateUserRequest;
import com.wuming.user.infrastructure.observability.TraceContext;
import com.wuming.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
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
        long start = System.currentTimeMillis();
        Long userId = userService.createUser(request);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            log.info("用户创建完成，costMs: {}", System.currentTimeMillis() - start);
        }
        return ApiResponse.success(userId);
    }

    /**
     * 查询当前可用用户基础信息。
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserDto> getUser(@PathVariable Long userId) {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            long start = System.currentTimeMillis();
            UserDto user = userService.getRequiredUser(userId);
            log.info("用户查询完成，costMs: {}", System.currentTimeMillis() - start);
            return ApiResponse.success(user);
        }
    }
}
