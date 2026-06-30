package com.wuming.user.rpc;

import com.wuming.api.user.UserFacade;
import com.wuming.api.user.dto.UserDto;
import com.wuming.api.user.dto.UserResultDto;
import com.wuming.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {
    private final UserService userService;

    @Override
    public UserResultDto getRequiredUser(Long userId) {
        long start = System.currentTimeMillis();
        log.info("UserFacade#getRequiredUser start, userId={}", userId);
        try {
            UserDto user = userService.getRequiredUser(userId);
            log.info("UserFacade#getRequiredUser query success, userId={}, costMs={}",
                    userId, System.currentTimeMillis() - start);
            return UserResultDto.success(user);
        } catch (IllegalArgumentException e) {
            log.info("UserFacade#getRequiredUser invalid, userId={}, costMs={}, message={}",
                    userId, System.currentTimeMillis() - start, e.getMessage());
            return UserResultDto.failure("USER_INVALID", e.getMessage());
        } catch (IllegalStateException e) {
            log.info("UserFacade#getRequiredUser unavailable, userId={}, costMs={}, message={}",
                    userId, System.currentTimeMillis() - start, e.getMessage());
            return UserResultDto.failure("USER_UNAVAILABLE", e.getMessage());
        } catch (Exception e) {
            log.error("UserFacade#getRequiredUser failed, userId={}, costMs={}",
                    userId, System.currentTimeMillis() - start, e);
            return UserResultDto.failure("SYSTEM_ERROR", e.getMessage());
        }
    }
}