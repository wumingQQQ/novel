package com.wuming.novel.rpc.user;

import com.wuming.api.user.UserFacade;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserContextService {
    @DubboReference(url = "tri://127.0.0.1:50052", timeout = 5000)
    private UserFacade userFacade;

    /**
     * 判断user是否存在或者处于active状态
     */
    public void requireUser(Long userId){
        if (userId == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        try {
            userFacade.getRequiredUser(userId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("用户服务暂时不可用", e);
        }
    }
}
