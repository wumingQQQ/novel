package com.wuming.user.controller;

import com.wuming.api.user.dto.UserDto;
import com.wuming.common.web.ApiResponse;
import com.wuming.user.domain.dto.LoginRequest;
import com.wuming.user.domain.dto.LoginResponse;
import com.wuming.user.domain.dto.RegisterRequest;
import com.wuming.user.service.AuthService;
import com.wuming.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserService userService;

    /**
     * 注册新用户
     */
    @PostMapping("/register")
    public ApiResponse<Long> register(@Valid @RequestBody RegisterRequest registerRequest) {
        long start = System.currentTimeMillis();
        Long userId = authService.register(registerRequest);
        log.info("用户注册成功，userId: {}, costMs: {}", userId, System.currentTimeMillis() - start);
        return ApiResponse.success(userId);
    }

    /**
     * 用户登录，成功后返回访问令牌
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse>  login(@Valid @RequestBody LoginRequest request) {
        long start = System.currentTimeMillis();
        LoginResponse response = authService.login(request);
        log.info("用户登录完成，account: {}, costMs: {}",
                request.getAccount(),
                System.currentTimeMillis() - start);
        return ApiResponse.success(response);
    }

    /**
     * 查询当前登录用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserDto> me(Authentication authentication) {
        Long userId = authService.requireUserId(authentication);
        return ApiResponse.success(userService.getRequiredUser(userId));
    }
}
