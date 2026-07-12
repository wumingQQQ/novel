package com.wuming.novel.controller;

import com.wuming.common.security.JwtUserIdExtractor;
import com.wuming.common.web.ApiResponse;
import com.wuming.novel.domain.dto.CreateRoleAdjustRequest;
import com.wuming.novel.domain.dto.ReviewRoleAdjustRequest;
import com.wuming.novel.domain.dto.ReviewRoleAdjustResult;
import com.wuming.novel.domain.dto.ReviseRoleAdjustResult;
import com.wuming.novel.domain.dto.RoleAdjustRequestDetailResponse;
import com.wuming.novel.domain.dto.RoleAdjustRequestSummaryResponse;
import com.wuming.novel.domain.dto.RoleAdjustEvidenceResponse;
import com.wuming.novel.domain.entity.RoleAdjustRequest;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.service.adjust.RoleAdjustService;
import com.wuming.novel.service.adjust.RoleAdjustQueryService;
import com.wuming.novel.service.adjust.RoleAdjustReviewService;
import com.wuming.novel.service.adjust.RoleAdjustRevisionService;
import com.wuming.novel.service.adjust.RoleAdjustVersionService;
import com.wuming.novel.service.adjust.RoleAdjustWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人角色调整接口。
 */
@Slf4j
@RestController
@RequestMapping("/role-adjust/requests")
@RequiredArgsConstructor
public class RoleAdjustController {
    private final RoleAdjustService roleAdjustService;
    private final RoleAdjustQueryService queryService;
    private final RoleAdjustReviewService reviewService;
    private final RoleAdjustRevisionService revisionService;
    private final RoleAdjustVersionService versionService;
    private final RoleAdjustWorkflowService workflowService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * 查询当前用户创建的角色调整请求摘要。
     *
     * @param authentication 当前认证上下文
     * @return 按最近更新时间排列的调整请求列表
     */
    @GetMapping
    public ApiResponse<List<RoleAdjustRequestSummaryResponse>> listRequests(Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            return ApiResponse.success(queryService.listRequests(userId));
        }
    }

    /**
     * 创建当前用户的个人角色调整请求。
     *
     * @param request 用户提交的调整目标
     * @param authentication 当前认证上下文
     * @return 新建调整请求主键
     */
    @PostMapping
    public ApiResponse<Long> create(
            @Valid @RequestBody CreateRoleAdjustRequest request,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            RoleAdjustRequest created = roleAdjustService.createRequest(userId, request);
            log.debug("角色调整请求创建完成，requestId: {}, characterId: {}",
                    created.getId(), created.getCharacterId());
            return ApiResponse.success(created.getId());
        }
    }

    /**
     * 查询当前用户拥有的角色调整请求详情。
     *
     * @param requestId 调整请求主键
     * @param authentication 当前认证上下文
     * @return 调整请求与候选项详情
     */
    @GetMapping("/{requestId}")
    public ApiResponse<RoleAdjustRequestDetailResponse> getDetail(
            @PathVariable Long requestId,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            return ApiResponse.success(queryService.getDetail(userId, requestId));
        }
    }

    @GetMapping("/{requestId}/items/{itemId}/evidences")
    public ApiResponse<List<RoleAdjustEvidenceResponse>> getItemEvidences(@PathVariable Long requestId,
            @PathVariable Long itemId, Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        return ApiResponse.success(queryService.getItemEvidences(userId, requestId, itemId));
    }

    /**
     * 为当前用户拥有的调整请求生成候选调整项。
     *
     * <p>当前实现同步执行生成流程；后续如果耗时不可接受，可在该入口后面替换为异步提交。</p>
     *
     * @param requestId 调整请求主键
     * @param authentication 当前认证上下文
     * @return 空结果，生成后的详情通过查询接口获取
     */
    @PostMapping("/{requestId}/generation")
    public ApiResponse<Void> generate(
            @PathVariable Long requestId,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            workflowService.generate(userId, requestId);
            log.debug("角色调整候选生成完成，requestId: {}", requestId);
            return ApiResponse.success();
        }
    }

    /**
     * 按用户反馈修订当前请求下所有 REVISING 候选项。
     *
     * @param requestId 调整请求主键
     * @param authentication 当前认证上下文
     * @return 逐项修订处理结果
     */
    @PostMapping("/{requestId}/revision")
    public ApiResponse<ReviseRoleAdjustResult> revise(
            @PathVariable Long requestId,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            ReviseRoleAdjustResult result = revisionService.revise(userId, requestId);
            log.debug("角色调整候选修订完成，requestId: {}, revisedCount: {}, errorCount: {}",
                    requestId,
                    result.getRevisedItemIds().size(),
                    result.getItemErrors().size());
            return ApiResponse.success(result);
        }
    }

    /**
     * 将已确认的调整请求落地为当前用户的个人角色版本。
     *
     * @param requestId 调整请求主键
     * @param authentication 当前认证上下文
     * @return 创建的个人角色版本主键
     */
    @PostMapping("/{requestId}/version")
    public ApiResponse<Long> createVersion(
            @PathVariable Long requestId,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            Long versionId = versionService.createVersion(userId, requestId);
            log.debug("角色调整请求已落地为个人版本，requestId: {}, versionId: {}", requestId, versionId);
            return ApiResponse.success(versionId);
        }
    }

    /**
     * 提交当前用户对角色调整候选项的评审结果。
     *
     * <p>候选项级错误会随结果返回，合法候选项仍会被处理。</p>
     *
     * @param requestId 调整请求主键
     * @param request 用户提交的候选项评审结果
     * @param authentication 当前认证上下文
     * @return 逐项评审处理结果
     */
    @PostMapping("/{requestId}/reviews")
    public ApiResponse<ReviewRoleAdjustResult> review(
            @PathVariable Long requestId,
            @Valid @RequestBody ReviewRoleAdjustRequest request,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            ReviewRoleAdjustResult result = reviewService.review(userId, requestId, request);
            log.debug("角色调整评审提交完成，requestId: {}, reviewedCount: {}, errorCount: {}, confirmed: {}",
                    requestId,
                    result.getReviewedItemIds().size(),
                    result.getItemErrors().size(),
                    result.isConfirmed());
            return ApiResponse.success(result);
        }
    }
}
