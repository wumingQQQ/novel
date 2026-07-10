package com.wuming.api.user;

import com.wuming.api.user.dto.UserResultDto;

/**
 * 用户远程接口降级实现，必须 fail closed。
 */
public class UserFacadeMock implements UserFacade {

    @Override
    public UserResultDto getRequiredUser(Long userId) {
        return UserResultDto.failure("REMOTE_SERVICE_ERROR", "用户服务暂时不可用");
    }
}
