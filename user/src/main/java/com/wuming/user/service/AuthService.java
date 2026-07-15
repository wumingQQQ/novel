package com.wuming.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.common.security.JwtProperties;
import com.wuming.user.domain.dto.CreateUserRequest;
import com.wuming.user.domain.dto.LoginRequest;
import com.wuming.user.domain.dto.LoginResponse;
import com.wuming.user.domain.dto.RegisterRequest;
import com.wuming.user.domain.entity.User;
import com.wuming.user.domain.enums.UserStatus;
import com.wuming.user.infrastructure.mapper.UserMapper;
import com.wuming.user.security.JwtTokenService;
import com.wuming.user.security.RefreshTokenIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserRefreshTokenService refreshTokenService;
    private final JwtProperties properties;

    /**
     * 注册新用户
     */
    public Long register(RegisterRequest request){
        CreateUserRequest createRequest = new CreateUserRequest();
        createRequest.setUsername(request.getUsername());
        createRequest.setNickname(request.getNickname());
        createRequest.setEmail(request.getEmail());
        createRequest.setPassword(request.getPassword());
        return userService.createUser(createRequest);
    }

    public LoginResponse login(LoginRequest request){
        User user = findByAccount(request.getAccount());
        if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())){
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if(!UserStatus.ACTIVE.name().equals(user.getStatus())){
            throw new BusinessException(ErrorCode.USER_DISABLED, "用户不可用");
        }
        return issueLoginResponse(user);
    }

    /**
     * 使用有效刷新令牌换取新的访问令牌和刷新令牌。
     */
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        Long userId = refreshTokenService.consume(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在: " + userId);
        }
        if(!UserStatus.ACTIVE.name().equals(user.getStatus())){
            throw new BusinessException(ErrorCode.USER_DISABLED, "用户不可用");
        }
        return issueLoginResponse(user);
    }

    /**
     * 吊销当前客户端持有的刷新令牌。
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private User findByAccount(String account){
        if(account == null || account.isBlank()){
            throw new IllegalArgumentException("account 不能为空");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, account.trim())
                        .or()
                        .eq(User::getEmail, account.trim())
        );
        if(user == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        return user;
    }

    /**
     * 获取当前登录用户id
     */
    public Long requireUserId(Authentication authentication) {
        if(authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)){
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }
        return Long.valueOf(jwt.getSubject());
    }

    private LoginResponse issueLoginResponse(User user) {
        String accessToken = jwtTokenService.createAccessToken(user);
        RefreshTokenIssue refreshToken = refreshTokenService.issue(user.getId());
        return new LoginResponse(
                accessToken,
                refreshToken.token(),
                "Bearer",
                properties.getExpiresMinutes() * 60,
                properties.getRefreshExpiresDays() * 24 * 60 * 60
        );
    }

}
