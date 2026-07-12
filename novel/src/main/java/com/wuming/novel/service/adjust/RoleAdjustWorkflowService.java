package com.wuming.novel.service.adjust;

import com.wuming.novel.domain.entity.RoleAdjustRequest;
import com.wuming.novel.domain.enums.RoleAdjustRequestStatus;
import com.wuming.novel.infrastructure.mapper.RoleAdjustRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAdjustWorkflowService {
    private final RoleAdjustRequestMapper requestMapper;
    private final RoleAdjustGenerationService generationService;
    private final RoleAdjustCandidatePersistenceService persistenceService;

    /**
     * 为待处理请求生成候选调整项，并将请求流转到待用户评审状态。
     *
     * <p>这里不包裹外层事务，避免生成或落库失败时 FAILED 状态被回滚。</p>
     */
    public void generate(Long requestId) {
        RoleAdjustRequest request = requirePendingRequest(requestId);
        generate(request);
    }

    /**
     * 为当前用户拥有的待处理请求生成候选调整项。
     *
     * @param userId 当前认证用户主键
     * @param requestId 调整请求主键
     */
    public void generate(Long userId, Long requestId) {
        RoleAdjustRequest request = requirePendingRequest(requestId);
        requireOwnedRequest(userId, request);
        generate(request);
    }

    /**
     * 执行候选生成和状态流转。
     */
    private void generate(RoleAdjustRequest request) {
        markGenerating(request.getId());
        try {
            RoleAdjustGenerationService.RoleAdjustGenerationResult result = generationService.generateCandidates(request);
            persistenceService.saveCandidates(
                    request.getId(),
                    result.candidates(),
                    result.evidences(),
                    result.baselineAdjustments()
            );
            markReady(request.getId());
        } catch (RuntimeException e) {
            markFailed(request.getId(), e);
            throw e;
        }
    }

    /**
     * 读取请求并校验只有 PENDING 请求可以进入生成流程。
     */
    private RoleAdjustRequest requirePendingRequest(Long requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException("requestId不能为空");
        }
        RoleAdjustRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("角色调整请求不存在: " + requestId);
        }
        if (request.getStatus() != RoleAdjustRequestStatus.PENDING) {
            throw new IllegalStateException("只有PENDING状态的角色调整请求可以生成候选项");
        }
        return request;
    }

    /**
     * 校验调整请求属于当前用户，避免用户触发他人的候选生成流程。
     */
    private void requireOwnedRequest(Long userId, RoleAdjustRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (!Objects.equals(request.getUserId(), userId)) {
            throw new IllegalStateException("角色调整请求不属于当前用户");
        }
    }

    /**
     * 标记请求正在生成候选项。
     */
    private void markGenerating(Long requestId) {
        updateStatus(requestId, RoleAdjustRequestStatus.GENERATING, null);
    }

    /**
     * 标记请求候选项已生成完毕，等待用户评审。
     */
    private void markReady(Long requestId) {
        updateStatus(requestId, RoleAdjustRequestStatus.READY, null);
    }

    /**
     * 标记请求生成失败，并保留可展示的失败原因。
     */
    private void markFailed(Long requestId, RuntimeException exception) {
        log.debug("角色调整候选生成失败，requestId: {}, errorType: {}, errorMessage: {}",
                requestId, exception.getClass().getSimpleName(), exception.getMessage(), exception);
        updateStatus(requestId, RoleAdjustRequestStatus.FAILED, errorMessage(exception));
    }

    /**
     * 使用独立更新对象，避免把请求旧字段误写回数据库。
     */
    private void updateStatus(Long requestId, RoleAdjustRequestStatus status, String failureReason) {
        RoleAdjustRequest update = new RoleAdjustRequest();
        update.setId(requestId);
        update.setStatus(status);
        update.setFailureReason(failureReason);
        requestMapper.updateById(update);
    }

    /**
     * 截断异常信息，避免上游返回过长文本挤占数据库字段和日志。
     */
    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
