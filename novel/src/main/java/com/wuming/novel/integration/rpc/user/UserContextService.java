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
            timeout = 5000
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
            UserResultDto result = userFacade.getRequiredUser(userId);
            if(result == null || !result.isSuccess()){
                throw toBusinessException(result);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("用户服务调用失败", e);
            throw new BusinessException(ErrorCode.REMOTE_SERVICE_ERROR, "用户服务暂时不可用");
        }
    }

    private BusinessException toBusinessException(UserResultDto result) {
        if (result == null) {
            return new BusinessException(ErrorCode.REMOTE_SERVICE_ERROR, "用户服务暂时不可用");
        }
        String code = result.getCode();
        if ("USER_NOT_FOUND".equals(code)) {
            return new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if ("USER_DISABLED".equals(code)) {
            return new BusinessException(ErrorCode.USER_DISABLED, "用户不可用");
        }
        if ("USER_INVALID".equals(code)) {
            return new BusinessException(ErrorCode.PARAM_ERROR, "用户参数无效");
        }
        return new BusinessException(ErrorCode.REMOTE_SERVICE_ERROR, "用户服务暂时不可用");
    }
}
