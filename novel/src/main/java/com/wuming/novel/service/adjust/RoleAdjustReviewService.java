package com.wuming.novel.service.adjust;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.wuming.novel.domain.dto.ReviewRoleAdjustItemRequest;
import com.wuming.novel.domain.dto.ReviewRoleAdjustRequest;
import com.wuming.novel.domain.dto.ReviewRoleAdjustResult;
import com.wuming.novel.domain.entity.RoleAdjustItem;
import com.wuming.novel.domain.entity.RoleAdjustRequest;
import com.wuming.novel.domain.enums.RoleAdjustRequestStatus;
import com.wuming.novel.domain.enums.RoleAdjustStatus;
import com.wuming.novel.infrastructure.mapper.RoleAdjustItemMapper;
import com.wuming.novel.infrastructure.mapper.RoleAdjustRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;

/**
 * 个人角色调整候选项用户评审服务。
 */
@Service
@RequiredArgsConstructor
public class RoleAdjustReviewService {
    private static final int REVISION_FEEDBACK_LIMIT = 500;

    private final RoleAdjustRequestMapper requestMapper;
    private final RoleAdjustItemMapper itemMapper;
    private final RoleAdjustVersionService versionService;

    /**
     * 提交一次调整请求下部分或全部候选项的用户评审结果。
     *
     * <p>用户可能分批评审候选项。只有所有候选项的最终状态都是 ACCEPTED 或 REJECTED 时，
     * 请求才进入 CONFIRMED；仍存在 PENDING 或 REVISING 时保持 READY。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ReviewRoleAdjustResult review(Long userId, Long requestId, ReviewRoleAdjustRequest reviewRequest) {
        RoleAdjustRequest request = requireReviewableRequest(userId, requestId);
        List<RoleAdjustItem> items = loadItems(requestId);
        Map<Long, RoleAdjustItem> itemsById = itemMap(items);
        ReviewPreparation preparation = prepareReviews(reviewRequest);
        ReviewRoleAdjustResult result = new ReviewRoleAdjustResult();
        result.setRequestId(requestId);
        result.getItemErrors().addAll(preparation.itemErrors());

        Map<Long, ReviewRoleAdjustItemRequest> validReviewsByItemId = new LinkedHashMap<>();
        for (ReviewRoleAdjustItemRequest review : preparation.reviewsByItemId().values()) {
            String errorMessage = validateReview(itemsById, review);
            if (errorMessage == null) {
                validReviewsByItemId.put(review.getItemId(), review);
            } else {
                result.getItemErrors().add(new ReviewRoleAdjustResult.ItemError(review.getItemId(), errorMessage));
            }
        }

        for (ReviewRoleAdjustItemRequest review : validReviewsByItemId.values()) {
            updateItemReview(review.getItemId(), review);
            result.getReviewedItemIds().add(review.getItemId());
        }

        if (!validReviewsByItemId.isEmpty() && allItemsFinalized(items, validReviewsByItemId)) {
            markConfirmed(request.getId());
            result.setConfirmed(true);
            if (hasAcceptedItem(items, validReviewsByItemId)) {
                // 评审闭环后立即生成个人版本，避免前端再补一次/version调用。
                Long versionId = versionService.createVersion(userId, requestId);
                result.setCreatedVersionId(versionId);
            }
        }
        return result;
    }

    /**
     * 校验请求存在、属于当前用户且处于可评审状态。
     */
    private RoleAdjustRequest requireReviewableRequest(Long userId, Long requestId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("requestId不能为空");
        }
        RoleAdjustRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("角色调整请求不存在: " + requestId);
        }
        if (!Objects.equals(request.getUserId(), userId)) {
            throw new IllegalStateException("角色调整请求不属于当前用户");
        }
        if (request.getStatus() != RoleAdjustRequestStatus.READY) {
            throw new IllegalStateException("只有READY状态的角色调整请求可以评审");
        }
        return request;
    }

    /**
     * 读取当前请求下的候选项。
     */
    private List<RoleAdjustItem> loadItems(Long requestId) {
        List<RoleAdjustItem> items = itemMapper.selectList(new LambdaQueryWrapper<RoleAdjustItem>()
                .eq(RoleAdjustItem::getRequestId, requestId)
                .orderByAsc(RoleAdjustItem::getDisplayOrder));
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("角色调整请求没有可评审候选项");
        }
        return items;
    }

    /**
     * 将候选项按主键映射，便于校验提交项归属。
     */
    private Map<Long, RoleAdjustItem> itemMap(List<RoleAdjustItem> items) {
        Map<Long, RoleAdjustItem> result = new LinkedHashMap<>();
        for (RoleAdjustItem item : items) {
            result.put(item.getId(), item);
        }
        return result;
    }

    /**
     * 将用户提交的评审结果按候选项主键映射。
     */
    private ReviewPreparation prepareReviews(ReviewRoleAdjustRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("评审结果不能为空");
        }
        Map<Long, ReviewRoleAdjustItemRequest> result = new LinkedHashMap<>();
        List<ReviewRoleAdjustResult.ItemError> itemErrors = new ArrayList<>();
        Set<Long> duplicatedItemIds = new HashSet<>();
        for (ReviewRoleAdjustItemRequest item : request.getItems()) {
            if (item == null || item.getItemId() == null) {
                itemErrors.add(new ReviewRoleAdjustResult.ItemError(null, "评审候选项ID不能为空"));
                continue;
            }
            if (result.put(item.getItemId(), item) != null) {
                result.remove(item.getItemId());
                if (duplicatedItemIds.add(item.getItemId())) {
                    itemErrors.add(new ReviewRoleAdjustResult.ItemError(item.getItemId(),
                            "重复提交候选项评审: " + item.getItemId()));
                }
            } else if (duplicatedItemIds.contains(item.getItemId())) {
                result.remove(item.getItemId());
            }
        }
        return new ReviewPreparation(result, itemErrors);
    }

    /**
     * 校验单个评审状态及改写反馈。
     */
    private String validateReview(Map<Long, RoleAdjustItem> itemsById, ReviewRoleAdjustItemRequest review) {
        if (!itemsById.containsKey(review.getItemId())) {
            return "候选项不属于当前调整请求: " + review.getItemId();
        }
        if (review.getStatus() == null) {
            return "评审状态不能为空";
        }
        if (review.getStatus() == RoleAdjustStatus.PENDING) {
            return "不能将候选项评审为PENDING";
        }
        if (review.getStatus() == RoleAdjustStatus.REVISING
                && (review.getRevisionFeedback() == null || review.getRevisionFeedback().isBlank())) {
            return "改写候选项必须填写反馈意见";
        }
        return null;
    }

    /**
     * 更新单个候选项的评审结果。
     */
    private void updateItemReview(Long itemId, ReviewRoleAdjustItemRequest review) {
        RoleAdjustItem update = new RoleAdjustItem();
        update.setId(itemId);
        update.setStatus(review.getStatus());
        update.setRevisionFeedback(trimRevisionFeedback(review));
        update.setRevisionError(null);
        itemMapper.updateById(update);
        clearReviewState(itemId, review.getStatus());
    }

    /**
     * MyBatis-Plus 默认不会通过 updateById 写入 null，这里显式清理评审状态字段。
     */
    private void clearReviewState(Long itemId, RoleAdjustStatus status) {
        UpdateWrapper<RoleAdjustItem> wrapper = new UpdateWrapper<RoleAdjustItem>()
                .eq("id", itemId)
                .set("revision_error", null);
        if (status != RoleAdjustStatus.REVISING) {
            wrapper.set("revision_feedback", null);
        }
        itemMapper.update(null, wrapper);
    }

    /**
     * 合并“数据库已有状态”和“本次提交状态”，判断整个请求是否已经评审完毕。
     */
    private boolean allItemsFinalized(List<RoleAdjustItem> items,
                                      Map<Long, ReviewRoleAdjustItemRequest> reviewsByItemId) {
        for (RoleAdjustItem item : items) {
            RoleAdjustStatus finalStatus = finalStatus(item, reviewsByItemId);
            if (finalStatus != RoleAdjustStatus.ACCEPTED && finalStatus != RoleAdjustStatus.REJECTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 本次提交覆盖的候选项以提交状态为准，未提交的候选项沿用已有状态。
     */
    private RoleAdjustStatus finalStatus(RoleAdjustItem item,
                                         Map<Long, ReviewRoleAdjustItemRequest> reviewsByItemId) {
        ReviewRoleAdjustItemRequest review = reviewsByItemId.get(item.getId());
        return review == null ? item.getStatus() : review.getStatus();
    }

    /**
     * 判断最终评审结果中是否存在需要落地的有效调整项。
     */
    private boolean hasAcceptedItem(List<RoleAdjustItem> items,
                                    Map<Long, ReviewRoleAdjustItemRequest> reviewsByItemId) {
        for (RoleAdjustItem item : items) {
            if (finalStatus(item, reviewsByItemId) == RoleAdjustStatus.ACCEPTED) {
                return true;
            }
        }
        return false;
    }

    /**
     * 标记请求评审完成；存在已接受项时会在同一事务内继续创建个人角色版本。
     */
    private void markConfirmed(Long requestId) {
        RoleAdjustRequest update = new RoleAdjustRequest();
        update.setId(requestId);
        update.setStatus(RoleAdjustRequestStatus.CONFIRMED);
        update.setFailureReason(null);
        requestMapper.updateById(update);
    }

    private String trimRevisionFeedback(ReviewRoleAdjustItemRequest review) {
        if (review.getStatus() != RoleAdjustStatus.REVISING) {
            return null;
        }
        String feedback = review.getRevisionFeedback().trim();
        return feedback.length() > REVISION_FEEDBACK_LIMIT
                ? feedback.substring(0, REVISION_FEEDBACK_LIMIT)
                : feedback;
    }

    /**
     * 预处理后的评审提交，拆分为可继续校验的候选项与已发现的逐项错误。
     */
    private record ReviewPreparation(Map<Long, ReviewRoleAdjustItemRequest> reviewsByItemId,
                                     List<ReviewRoleAdjustResult.ItemError> itemErrors) {
    }
}
