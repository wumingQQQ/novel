package com.wuming.user.controller;

import com.wuming.common.web.ApiResponse;
import com.wuming.user.domain.dto.LoginRequest;
import com.wuming.user.domain.dto.LoginResponse;
import com.wuming.user.domain.dto.RegisterRequest;
import com.wuming.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

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
    @PostMapping
    public ApiResponse<LoginResponse>  login(@Valid @RequestBody LoginRequest request) {
        long start = System.currentTimeMillis();
        LoginResponse response = authService.login(request);
        log.info("用户登录完成，account: {}, costMs: {}",
                request.getAccount(),
                System.currentTimeMillis() - start);
        return ApiResponse.success(response);
    }
}
