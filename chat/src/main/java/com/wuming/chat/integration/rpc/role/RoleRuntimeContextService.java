package com.wuming.chat.integration.rpc.role;

import com.wuming.api.role.RoleRuntimeFacade;
import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.api.role.dto.RoleRuntimeContextResultDto;
import com.wuming.api.role.dto.RoleVersionValidationResultDto;
import com.wuming.chat.infrastructure.cache.RpcResultCacheService;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleRuntimeContextService {
    private final RpcResultCacheService rpcResultCacheService;

    @DubboReference(
            url = "${chat.role-runtime.url:tri://127.0.0.1:50051}",
            timeout = 10000,
            mock = "com.wuming.api.role.RoleRuntimeFacadeMock"
    )
    private RoleRuntimeFacade roleRuntimeFacade;

    /**
     * 根据角色id从缓存读取运行时上下文，缓存未命中时返回null。
     *
     * @param characterId 角色id
     * @return 角色运行时上下文
     */
    public RoleRuntimeContextDto getCachedRuntimeContext(Long characterId) {
        if (characterId == null) {
            return null;
        }
        return rpcResultCacheService.getRoleRuntimeContext(characterId);
    }

    /**
     * 优先按角色id读缓存，未命中时远程回源。
     *
     * @param characterId 角色id
     * @return 角色运行时上下文
     */
    public RoleRuntimeContextDto getRuntimeContext(Long characterId) {
        RoleRuntimeContextDto cachedContext = getCachedRuntimeContext(characterId);
        if (cachedContext != null) {
            return cachedContext;
        }
        return getRemoteRuntimeContext(characterId);
    }

    /**
     * 获取聊天会话实际使用的运行时上下文。
     *
     * <p>公共角色上下文按characterId缓存；个人版本会叠加用户补丁，不能复用公共缓存。</p>
     *
     * @param userId 当前用户主键
     * @param characterId 公共角色主键
     * @param userRoleVersionId 个人角色版本主键，可为空
     * @return 角色运行时上下文
     */
    public RoleRuntimeContextDto getRuntimeContext(Long userId, Long characterId, Long userRoleVersionId) {
        if (userRoleVersionId == null) {
            return getRuntimeContext(characterId);
        }
        return getRemoteRuntimeContext(userId, characterId, userRoleVersionId);
    }

    /**
     * 远程校验个人角色版本确属当前用户和目标公共角色。
     *
     * @return 校验通过时为true；校验服务失败或归属不符时返回false
     */
    public boolean validateUserRoleVersion(Long userId, Long characterId, Long userRoleVersionId) {
        RoleVersionValidationResultDto result = roleRuntimeFacade.validateUserRoleVersion(
                userId, characterId, userRoleVersionId);
        if (result == null) {
            log.warn("个人角色版本校验远程返回为空，userId: {}, characterId: {}, versionId: {}",
                    userId, characterId, userRoleVersionId);
            return false;
        }
        if (!result.isValid()) {
            log.info("个人角色版本校验未通过，userId: {}, characterId: {}, versionId: {}, code: {}",
                    userId, characterId, userRoleVersionId, result.getCode());
        }
        return result.isValid();
    }

    private RoleRuntimeContextDto getRemoteRuntimeContext(Long characterId) {
        return getRemoteRuntimeContext(null, characterId, null);
    }

    private RoleRuntimeContextDto getRemoteRuntimeContext(
            Long userId, Long characterId, Long userRoleVersionId) {
        long start = System.currentTimeMillis();
        log.debug("开始远程查询角色运行时上下文，userId: {}, characterId: {}, versionId: {}",
                userId, characterId, userRoleVersionId);
        RoleRuntimeContextResultDto result = userRoleVersionId == null
                ? roleRuntimeFacade.getRuntimeContext(characterId)
                : roleRuntimeFacade.getRuntimeContext(userId, characterId, userRoleVersionId);
        if (result == null) {
            log.warn("远程角色运行时上下文查询返回空，userId: {}, characterId: {}, versionId: {}, costMs: {}",
                    userId, characterId, userRoleVersionId, System.currentTimeMillis() - start);
            throw new BusinessException(ErrorCode.PROFILE_CONTEXT_NOT_FOUND, "角色运行时服务返回为空");
        }
        if (!result.isSuccess()) {
            log.info("远程角色运行时上下文查询未成功，userId: {}, characterId: {}, versionId: {}, code: {}, costMs: {}, message: {}",
                    userId, characterId, userRoleVersionId, result.getCode(),
                    System.currentTimeMillis() - start, result.getMessage());
            throw new BusinessException(ErrorCode.PROFILE_CONTEXT_NOT_FOUND, result.getMessage());
        }
        log.debug("远程角色运行时上下文查询完成，characterId: {}, versionId: {}, costMs: {}",
                result.getRuntimeContext().getCharacterId(), userRoleVersionId, System.currentTimeMillis() - start);
        if (userRoleVersionId == null) {
            cacheRuntimeContext(result.getRuntimeContext());
        }
        return result.getRuntimeContext();
    }

    private void cacheRuntimeContext(RoleRuntimeContextDto context) {
        if (context == null || context.getCharacterId() == null) {
            return;
        }
        rpcResultCacheService.putRoleRuntimeContext(context.getCharacterId(), context);
    }
}
