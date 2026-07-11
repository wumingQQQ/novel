package com.wuming.api.role;

import com.wuming.api.role.dto.RoleRuntimeContextResultDto;
import com.wuming.api.role.dto.RoleVersionValidationResultDto;

/**
 * 角色运行时远程接口降级实现。
 */
public class RoleRuntimeFacadeMock implements RoleRuntimeFacade {

    @Override
    public RoleRuntimeContextResultDto getRuntimeContext(Long characterId) {
        return RoleRuntimeContextResultDto.failure("ROLE_RUNTIME_UNAVAILABLE", "角色运行时服务不可用");
    }

    @Override
    public RoleVersionValidationResultDto validateUserRoleVersion(
            Long userId, Long characterId, Long userRoleVersionId) {
        return RoleVersionValidationResultDto.failure(
                "ROLE_RUNTIME_UNAVAILABLE", "角色版本校验服务不可用");
    }
}
