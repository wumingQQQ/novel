package com.wuming.chat.integration.rpc.user;

import com.wuming.api.user.UserFacade;
import com.wuming.api.user.dto.UserResultDto;
import com.wuming.chat.infrastructure.observability.TraceContext;
import com.wuming.chat.infrastructure.cache.RpcResultCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserContextService {

    private final RpcResultCacheService rpcResultCacheService;

    @DubboReference(
            url = "tri://127.0.0.1:50052",
            timeout = 15000
    )
    private UserFacade userFacade;

    /**
     * 通过缓存或远程调用确认用户存在且可用。
     *
     * @param userId 用户id
     * @return 用户基础信息
     */
    public UserResultDto getRequiredUser(Long userId) {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            UserResultDto cachedResult = rpcResultCacheService.getUserResult(userId);
            if (cachedResult != null) {
                return cachedResult;
            }

            long start = System.currentTimeMillis();
            log.debug("开始远程校验用户");
            try {
                UserResultDto result = userFacade.getRequiredUser(userId);
                log.debug("远程用户校验返回，success: {}, costMs: {}",
                        result != null && result.isSuccess(), System.currentTimeMillis() - start);
                if (result != null && result.isSuccess()) {
                    rpcResultCacheService.putUserResult(userId, result);
                }
                return result != null
                        ? result
                        : UserResultDto.failure("REMOTE_SERVICE_ERROR", "用户服务暂时不可用");
            } catch (RuntimeException e) {
                log.warn("远程用户校验调用失败，costMs: {}",
                        System.currentTimeMillis() - start, e);
                return UserResultDto.failure("REMOTE_SERVICE_ERROR", "用户服务暂时不可用");
            }
        }
    }
}

