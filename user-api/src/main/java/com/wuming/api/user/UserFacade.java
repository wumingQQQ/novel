package com.wuming.api.user;

import com.wuming.api.user.dto.UserResultDto;

public interface UserFacade {

    /**
     * 获取可用用户；用户不存在或不可用时返回失败结果
     *
     * @param userId 用户id
     * @return 统一响应
     */
    UserResultDto getRequiredUser(Long userId);
}
