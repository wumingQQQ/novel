package com.wuming.api.role;

import com.wuming.api.role.dto.RoleRuntimeContextResultDto;

/**
 * 角色运行时远程接口。
 */
public interface RoleRuntimeFacade {

    /**
     * 根据角色获取角色运行时上下文。
     *
     * @param characterId 角色id
     * @return 角色运行时上下文
     */
    RoleRuntimeContextResultDto getRuntimeContext(Long characterId);
}
