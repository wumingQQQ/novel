package com.wuming.novel.integration.rpc.user;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.api.user.UserFacade;
import com.wuming.api.user.dto.UserResultDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserContextService {
    @DubboReference(
            url = "tri://127.0.0.1:50052",
            timeout = 5000,
            mock = "com.wuming.api.user.UserFacadeMock"
    )
    private UserFacade userFacade;

    /**
     * 判断user是否存在或者处于active状态
     */
    public void requireUser(Long userId){
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不能为空");
        }
        try {
            UserResultDto user = userFacade.getRequiredUser(userId);
            if(!user.isSuccess()){
                throw new BusinessException(ErrorCode.USER_NOT_FOUND, user.getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.REMOTE_SERVICE_ERROR, "用户服务暂时不可用");
        }
    }
}
