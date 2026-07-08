package com.wuming.chat.integration.rpc.role;

import com.wuming.api.role.RoleRuntimeFacade;
import com.wuming.api.role.dto.RoleRuntimeContextDto;
import com.wuming.api.role.dto.RoleRuntimeContextResultDto;
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

    private RoleRuntimeContextDto getRemoteRuntimeContext(Long characterId) {
        long start = System.currentTimeMillis();
        log.debug("开始远程查询角色运行时上下文，characterId: {}", characterId);
        RoleRuntimeContextResultDto result = roleRuntimeFacade.getRuntimeContext(characterId);
        if (result == null) {
            log.warn("远程角色运行时上下文查询返回空，characterId: {}, costMs: {}",
                    characterId, System.currentTimeMillis() - start);
            throw new BusinessException(ErrorCode.PROFILE_CONTEXT_NOT_FOUND, "角色运行时服务返回为空");
        }
        if (!result.isSuccess()) {
            log.info("远程角色运行时上下文查询未成功，characterId: {}, code: {}, costMs: {}, message: {}",
                    characterId, result.getCode(), System.currentTimeMillis() - start, result.getMessage());
            throw new BusinessException(ErrorCode.PROFILE_CONTEXT_NOT_FOUND, result.getMessage());
        }
        log.debug("远程角色运行时上下文查询完成，characterId: {}, costMs: {}",
                result.getRuntimeContext().getCharacterId(), System.currentTimeMillis() - start);
        cacheRuntimeContext(result.getRuntimeContext());
        return result.getRuntimeContext();
    }

    private void cacheRuntimeContext(RoleRuntimeContextDto context) {
        if (context == null || context.getCharacterId() == null) {
            return;
        }
        rpcResultCacheService.putRoleRuntimeContext(context.getCharacterId(), context);
    }
}
