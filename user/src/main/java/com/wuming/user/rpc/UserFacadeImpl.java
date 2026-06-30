package com.wuming.user.rpc;

import com.wuming.api.user.UserFacade;
import com.wuming.api.user.dto.UserDto;
import com.wuming.api.user.dto.UserResultDto;
import com.wuming.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {
    private final UserService userService;

    @Override
    public UserResultDto getRequiredUser(Long userId) {
        try {
            return UserResultDto.success(userService.getRequiredUser(userId));
        } catch (IllegalArgumentException e) {
            return UserResultDto.failure("USER_INVALID", e.getMessage());
        } catch (IllegalStateException e) {
            return UserResultDto.failure("USER_UNAVAILABLE", e.getMessage());
        }
        catch (Exception e) {
            return UserResultDto.failure("SYSTEM_ERROR", e.getMessage());
        }
    }
}
