package com.wuming.user.controller;

import com.wuming.api.user.dto.UserDto;
import com.wuming.common.web.ApiResponse;
import com.wuming.user.domain.dto.LoginRequest;
import com.wuming.user.domain.dto.LoginResponse;
import com.wuming.user.domain.dto.RegisterRequest;
import com.wuming.user.security.RefreshTokenCookieService;
import com.wuming.user.service.AuthService;
import com.wuming.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final RefreshTokenCookieService refreshTokenCookieService;

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
    public ApiResponse<LoginResponse>  login(@Valid @RequestBody LoginRequest request, HttpServletResponse servletResponse) {
        long start = System.currentTimeMillis();
        LoginResponse response = authService.login(request);
        refreshTokenCookieService.write(servletResponse, response.getRefreshToken(), response.getRefreshExpiresIn());
        log.info("用户登录完成，account: {}, costMs: {}",
                request.getAccount(),
                System.currentTimeMillis() - start);
        return ApiResponse.success(response);
    }

    /**
     * 使用刷新令牌换取新的访问令牌，并轮换刷新令牌。
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest servletRequest,
                                              HttpServletResponse servletResponse) {
        try {
            LoginResponse response = authService.refresh(refreshTokenCookieService.resolve(servletRequest));
            refreshTokenCookieService.write(servletResponse, response.getRefreshToken(), response.getRefreshExpiresIn());
            return ApiResponse.success(response);
        } catch (RuntimeException e) {
            refreshTokenCookieService.clear(servletResponse);
            throw e;
        }
    }

    /**
     * 退出当前客户端登录态，吊销刷新令牌。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest servletRequest,
                                    HttpServletResponse servletResponse) {
        try {
            authService.logout(refreshTokenCookieService.resolve(servletRequest));
            return ApiResponse.success();
        } finally {
            refreshTokenCookieService.clear(servletResponse);
        }
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
