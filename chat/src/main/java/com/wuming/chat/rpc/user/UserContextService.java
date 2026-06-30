package com.wuming.chat.rpc.user;

import com.wuming.api.user.UserFacade;
import com.wuming.api.user.dto.UserResultDto;
import com.wuming.chat.observability.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserContextService {

    @DubboReference(url = "tri://127.0.0.1:50052", timeout = 15000)
    private UserFacade userFacade;

    /**
     * 通过远程调用确认用户存在且可用。
     *
     * @param userId 用户id
     * @return 用户基础信息
     */
    public UserResultDto getRequiredUser(Long userId) {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            long start = System.currentTimeMillis();
            log.debug("开始远程校验用户");
            UserResultDto result = userFacade.getRequiredUser(userId);
            log.debug("远程用户校验返回，success: {}, costMs: {}",
                    result != null && result.isSuccess(), System.currentTimeMillis() - start);
            return result;
        }
    }
}
