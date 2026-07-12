package com.wuming.novel.service.adjust;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.dto.RoleAdjustRequestDetailResponse;
import com.wuming.novel.domain.entity.RoleAdjustEvidence;
import com.wuming.novel.domain.entity.RoleAdjustItem;
import com.wuming.novel.domain.entity.RoleAdjustRequest;
import com.wuming.novel.infrastructure.mapper.RoleAdjustEvidenceMapper;
import com.wuming.novel.infrastructure.mapper.RoleAdjustItemMapper;
import com.wuming.novel.infrastructure.mapper.RoleAdjustRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 个人角色调整请求查询服务。
 */
@Service
@RequiredArgsConstructor
public class RoleAdjustQueryService {
    private final RoleAdjustRequestMapper requestMapper;
    private final RoleAdjustItemMapper itemMapper;
    private final RoleAdjustEvidenceMapper evidenceMapper;

    /**
     * 查询当前用户拥有的角色调整请求详情。
     *
     * @param userId 当前认证用户主键
     * @param requestId 调整请求主键
     * @return 调整请求与候选项详情
     */
    public RoleAdjustRequestDetailResponse getDetail(Long userId, Long requestId) {
        RoleAdjustRequest request = requireOwnedRequest(userId, requestId);
        List<RoleAdjustItem> items = loadItems(requestId);
        Map<Long, List<Long>> passageIdsByItemId = loadPassageIdsByItemId(items);
        return toDetail(request, items, passageIdsByItemId);
    }

    /**
     * 校验请求存在且属于当前用户。
     */
    private RoleAdjustRequest requireOwnedRequest(Long userId, Long requestId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("requestId不能为空");
        }
        RoleAdjustRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色调整请求不存在: " + requestId);
        }
        if (!Objects.equals(request.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "角色调整请求不属于当前用户");
        }
        return request;
    }

    /**
     * 按展示顺序读取请求下的候选项。
     */
    private List<RoleAdjustItem> loadItems(Long requestId) {
        List<RoleAdjustItem> items = itemMapper.selectList(new LambdaQueryWrapper<RoleAdjustItem>()
                .eq(RoleAdjustItem::getRequestId, requestId)
                .orderByAsc(RoleAdjustItem::getDisplayOrder));
        return items == null ? List.of() : items;
    }

    /**
     * 批量读取候选项引用的原作 Passage 主键。
     */
    private Map<Long, List<Long>> loadPassageIdsByItemId(List<RoleAdjustItem> items) {
        List<Long> itemIds = items.stream()
                .map(RoleAdjustItem::getId)
                .filter(Objects::nonNull)
                .toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        List<RoleAdjustEvidence> evidences = evidenceMapper.selectList(new LambdaQueryWrapper<RoleAdjustEvidence>()
                .in(RoleAdjustEvidence::getItemId, itemIds));
        return evidenceMap(evidences);
    }

    /**
     * 将证据记录合并为 itemId -> passageIds，兼容历史上同一候选项存在多条证据记录的情况。
     */
    private Map<Long, List<Long>> evidenceMap(List<RoleAdjustEvidence> evidences) {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        if (evidences == null) {
            return result;
        }
        for (RoleAdjustEvidence evidence : evidences) {
            if (evidence.getItemId() == null || evidence.getPassageIds() == null) {
                continue;
            }
            result.computeIfAbsent(evidence.getItemId(), ignored -> new ArrayList<>())
                    .addAll(evidence.getPassageIds());
        }
        return result;
    }

    /**
     * 组装前端详情响应。
     */
    private RoleAdjustRequestDetailResponse toDetail(RoleAdjustRequest request,
                                                     List<RoleAdjustItem> items,
                                                     Map<Long, List<Long>> passageIdsByItemId) {
        List<RoleAdjustRequestDetailResponse.Item> itemResponses = items.stream()
                .map(item -> toItem(item, passageIdsByItemId.getOrDefault(item.getId(), List.of())))
                .toList();
        return new RoleAdjustRequestDetailResponse(
                request.getId(),
                request.getCharacterId(),
                request.getBaseVersionId(),
                request.getRequirement(),
                request.getChatText(),
                request.getStatus(),
                request.getFailureReason(),
                request.getCreatedVersionId(),
                request.getCreateTime(),
                request.getUpdateTime(),
                itemResponses
        );
    }

    /**
     * 组装单个候选项响应。
     */
    private RoleAdjustRequestDetailResponse.Item toItem(RoleAdjustItem item, List<Long> passageIds) {
        return new RoleAdjustRequestDetailResponse.Item(
                item.getId(),
                item.getChangeType(),
                item.getAdjustmentId(),
                item.getTargetAdjustmentId(),
                item.getApplicability(),
                item.getExpectedBehavior(),
                item.getForbiddenBehavior(),
                item.getStatus(),
                item.getRevisionFeedback(),
                item.getRevisionError(),
                item.getDisplayOrder(),
                List.copyOf(passageIds),
                item.getCreateTime(),
                item.getUpdateTime()
        );
    }
}
