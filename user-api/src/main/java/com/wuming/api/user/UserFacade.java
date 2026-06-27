package com.wuming.api.user;

import com.wuming.api.user.dto.UserDto;

public interface UserFacade {

    /**
     * 获取可用用户；用户不存在或不可用时抛出异常。
     *
     * @param userId 用户id
     * @return 用户基础信息
     */
    UserDto getRequiredUser(Long userId);
}
