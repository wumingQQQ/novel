package com.wuming.user.rpc;

import com.wuming.api.user.UserFacade;
import com.wuming.api.user.dto.UserDto;
import com.wuming.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {
    private final UserService userService;

    @Override
    public UserDto getRequiredUser(Long userId) {
        return userService.getRequiredUser(userId);
    }
}
