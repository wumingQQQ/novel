package com.wuming.api.user;

import com.wuming.api.user.dto.UserDto;
import com.wuming.api.user.dto.UserResultDto;

/**
 * 用户远程接口降级实现。
 *
 * <p>供 Dubbo consumer mock 使用。当用户服务不可用、超时或远程调用异常时，
 * 返回可继续主流程的默认用户结果，便于本地测试和链路调试。</p>
 */
public class UserFacadeMock implements UserFacade {

    @Override
    public UserResultDto getRequiredUser(Long userId) {
        UserDto user = new UserDto();
        user.setId(userId);
        user.setUsername("fallback_user");
        user.setNickname("降级用户");
        user.setStatus("ACTIVE");
        return UserResultDto.success(user);
    }
}
