package com.wuming.api.role;

import com.wuming.api.role.dto.RoleRuntimeContextResultDto;
import com.wuming.api.role.dto.RoleVersionValidationResultDto;

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

    /**
     * 根据公共角色和可选个人版本获取角色运行时上下文。
     *
     * @param userId 当前用户主键
     * @param characterId 公共角色主键
     * @param userRoleVersionId 个人角色版本主键，可为空
     * @return 角色运行时上下文
     */
    RoleRuntimeContextResultDto getRuntimeContext(
            Long userId, Long characterId, Long userRoleVersionId);

    /**
     * 校验个人角色版本属于指定用户且源自指定公共角色。
     *
     * @param userId 当前用户主键
     * @param characterId 公共角色主键
     * @param userRoleVersionId 个人角色版本主键
     * @return 归属校验结果
     */
    RoleVersionValidationResultDto validateUserRoleVersion(
            Long userId, Long characterId, Long userRoleVersionId);
}
