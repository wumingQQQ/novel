package com.wuming.api.role;

import com.wuming.api.role.dto.RoleRuntimeContextResultDto;

/**
 * 角色运行时远程接口降级实现。
 */
public class RoleRuntimeFacadeMock implements RoleRuntimeFacade {

    @Override
    public RoleRuntimeContextResultDto getRuntimeContext(Long characterId) {
        return RoleRuntimeContextResultDto.failure("ROLE_RUNTIME_UNAVAILABLE", "角色运行时服务不可用");
    }
}
