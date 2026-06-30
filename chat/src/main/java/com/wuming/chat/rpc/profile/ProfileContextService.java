package com.wuming.chat.rpc.profile;

import com.wuming.api.profile.RoleContextFacade;
import com.wuming.api.profile.dto.RoleContextDto;
import com.wuming.api.profile.dto.RoleContextResultDto;
import com.wuming.chat.observability.TraceContext;
import com.wuming.chat.service.cache.RpcResultCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileContextService {

    private final RpcResultCacheService rpcResultCacheService;

    @DubboReference(url = "tri://127.0.0.1:50051", timeout = 10000)
    private RoleContextFacade roleContextFacade;

    /**
     * 通过缓存或远程调用获取角色画像，并将远程失败转换为本地业务异常。
     *
     * @param jobId 与画像关联的job
     * @return 角色画像上下文
     */
    public RoleContextDto getProfileContext(Long jobId) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            RoleContextDto cachedContext = rpcResultCacheService.getRoleContext(jobId);
            if (cachedContext != null) {
                return cachedContext;
            }

            long start = System.currentTimeMillis();
            log.debug("开始远程查询角色画像");
            RoleContextResultDto context = roleContextFacade.getRoleContext(jobId);
            if (context == null) {
                log.warn("远程角色画像查询返回空，costMs: {}", System.currentTimeMillis() - start);
                throw new IllegalStateException("画像服务返回为空");
            }
            if (!context.isSuccess()) {
                log.info("远程角色画像查询未成功，code: {}, costMs: {}, message: {}",
                        context.getCode(), System.currentTimeMillis() - start,
                        context.getMessage());
                throw new IllegalStateException(context.getMessage());
            }
            log.debug("远程角色画像查询完成，costMs: {}", System.currentTimeMillis() - start);
            rpcResultCacheService.putRoleContext(jobId, context.getRoleContext());
            return context.getRoleContext();
        }
    }
}
