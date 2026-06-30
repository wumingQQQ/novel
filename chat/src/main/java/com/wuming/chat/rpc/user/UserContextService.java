package com.wuming.chat.rpc.user;

import com.wuming.api.user.UserFacade;
import com.wuming.api.user.dto.UserResultDto;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

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
        return userFacade.getRequiredUser(userId);
    }
}
