package com.wuming.chat.service;

import com.wuming.api.profile.RoleContextFacade;
import com.wuming.api.profile.dto.RoleContextDto;
import com.wuming.api.profile.dto.RoleContextResultDto;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileContextService {

    // TODO 后期考虑优化耗时，或缓存角色画像数据
    @DubboReference(url = "tri://127.0.0.1:50051", timeout = 10000)
    private RoleContextFacade roleContextFacade;

    /**
     * 通过远程调用获取角色画像
     *
     * @param jobId 与画像关联的job
     */
    public RoleContextDto getProfileContext(Long jobId) {
        RoleContextResultDto context = roleContextFacade.getRoleContext(jobId);
        if (context == null) {
            throw new IllegalStateException("画像服务返回为空");
        }
        if (!context.isSuccess()) {
            throw new IllegalStateException(context.getMessage());
        }
        return context.getRoleContext();
    }
}
