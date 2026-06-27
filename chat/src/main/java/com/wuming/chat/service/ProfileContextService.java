package com.wuming.chat.service;

import com.wuming.api.profile.RoleContextFacade;
import com.wuming.api.profile.dto.RoleContextDto;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileContextService {

    @DubboReference(url = "tri://127.0.0.1:50051")
    private RoleContextFacade roleContextFacade;

    /**
     * 通过远程调用获取角色画像
     * @param jobId 与画像关联的job
     */
    public RoleContextDto getProfileContext(Long jobId){
        return roleContextFacade.getRoleContext(jobId);
    }
}
