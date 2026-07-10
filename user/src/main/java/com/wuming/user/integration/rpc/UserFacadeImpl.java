package com.wuming.user.integration.rpc;

import com.wuming.api.user.UserFacade;
import com.wuming.api.user.dto.UserDto;
import com.wuming.api.user.dto.UserResultDto;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.user.infrastructure.observability.TraceContext;
import com.wuming.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {
    private final UserService userService;

    /**
     * 校验并返回指定用户信息，供其他模块通过Dubbo确认用户是否可用。
     *
     * @param userId 用户id
     * @return 用户查询结果，失败时通过code和message描述原因
     */
    @Override
    public UserResultDto getRequiredUser(Long userId) {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            long start = System.currentTimeMillis();
            log.info("开始校验远程用户");
            try {
                UserDto user = userService.getRequiredUser(userId);
                log.info("远程用户校验完成，costMs: {}", System.currentTimeMillis() - start);
                return UserResultDto.success(user);
            } catch (BusinessException e) {
                String code = mapBusinessCode(e.getCode());
                log.info("远程用户校验未通过，code: {}, costMs: {}",
                        code, System.currentTimeMillis() - start);
                return UserResultDto.failure(code, safeBusinessMessage(code));
            } catch (IllegalArgumentException e) {
                log.info("远程用户校验未通过，code: USER_INVALID, costMs: {}",
                        System.currentTimeMillis() - start);
                return UserResultDto.failure("USER_INVALID", "用户参数无效");
            } catch (IllegalStateException e) {
                log.info("远程用户不可用，code: USER_DISABLED, costMs: {}",
                        System.currentTimeMillis() - start);
                return UserResultDto.failure("USER_DISABLED", "用户不可用");
            } catch (Exception e) {
                log.error("远程用户校验失败，code: SYSTEM_ERROR, costMs: {}",
                        System.currentTimeMillis() - start, e);
                return UserResultDto.failure("SYSTEM_ERROR", "用户服务内部错误");
            }
        }
    }

    private String mapBusinessCode(int code) {
        if (code == ErrorCode.USER_NOT_FOUND.getCode()) {
            return "USER_NOT_FOUND";
        }
        if (code == ErrorCode.USER_DISABLED.getCode()) {
            return "USER_DISABLED";
        }
        return "SYSTEM_ERROR";
    }

    private String safeBusinessMessage(String code) {
        return switch (code) {
            case "USER_NOT_FOUND" -> "用户不存在";
            case "USER_DISABLED" -> "用户不可用";
            default -> "用户服务内部错误";
        };
    }
}
