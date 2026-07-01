package com.wuming.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.user.config.AuthJwtProperties;
import com.wuming.user.domain.dto.CreateUserRequest;
import com.wuming.user.domain.dto.LoginRequest;
import com.wuming.user.domain.dto.LoginResponse;
import com.wuming.user.domain.dto.RegisterRequest;
import com.wuming.user.domain.entity.User;
import com.wuming.user.domain.enums.UserStatus;
import com.wuming.user.infrastructure.mapper.UserMapper;
import com.wuming.user.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthJwtProperties properties;

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
        String token = jwtTokenService.createAccessToken(user);
        return new LoginResponse(
                token,
                "Bearer",
                properties.getExpiresMinutes() * 60
        );
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


}
