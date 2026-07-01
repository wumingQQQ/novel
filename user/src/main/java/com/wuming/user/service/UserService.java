package com.wuming.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.api.user.dto.UserDto;
import com.wuming.user.domain.dto.CreateUserRequest;
import com.wuming.user.domain.entity.User;
import com.wuming.user.domain.enums.UserStatus;
import com.wuming.user.infrastructure.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    /**
     * 创建一个可用于开发环境验证的基础用户。
     */
    @Transactional
    public Long createUser(CreateUserRequest request) {
        if (request == null || request.getUsername() == null
                || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("username不能为空");
        }
        boolean exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername().trim())
        ) > 0;
        if (exists) {
            throw new BusinessException(ErrorCode.CONFLICT, "username已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setNickname(normalizeNickname(request.getNickname()));
        user.setStatus(UserStatus.ACTIVE.name());
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 查询可用用户；不存在或禁用时直接失败。
     */
    public UserDto getRequiredUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在: " + userId);
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "用户不可用: " + userId);
        }
        return toDto(user);
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        return nickname.trim();
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setStatus(user.getStatus());
        return dto;
    }
}
