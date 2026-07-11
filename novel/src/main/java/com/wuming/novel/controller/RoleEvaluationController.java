package com.wuming.novel.controller;

import com.wuming.common.security.JwtUserIdExtractor;
import com.wuming.common.web.ApiResponse;
import com.wuming.novel.domain.dto.ApplyRoleEvaluationImprovementBatchRequest;
import com.wuming.novel.domain.dto.CreateRoleEvaluationCasesRequest;
import com.wuming.novel.domain.dto.CreateRoleEvaluationImprovementBatchRequest;
import com.wuming.novel.domain.dto.CreateRoleEvaluationRequest;
import com.wuming.novel.domain.dto.RoleEvaluationReportResponse;
import com.wuming.novel.domain.entity.RoleEvaluation;
import com.wuming.novel.domain.entity.RoleEvaluationCase;
import com.wuming.novel.domain.entity.RoleEvaluationImprovementBatch;
import com.wuming.novel.domain.entity.RoleEvaluationRuleImprovement;
import com.wuming.novel.domain.entity.RoleEvaluationRun;
import com.wuming.novel.domain.entity.UserRoleVersion;
import com.wuming.novel.service.evaluation.RoleEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户独立角色评测、规则建议和个人规则覆写接口。
 */
@RestController
@RequestMapping("/role-evaluations")
@RequiredArgsConstructor
public class RoleEvaluationController {
    private final RoleEvaluationService evaluationService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * 用户选择一个已完成构建的公共角色，创建自己的独立评测。
     *
     * @param request 公共角色选择参数
     * @param authentication 当前认证信息
     * @return 新建评测
     */
    @PostMapping
    public ApiResponse<RoleEvaluation> createEvaluation(
            @Valid @RequestBody CreateRoleEvaluationRequest request, Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        return ApiResponse.success(evaluationService.createEvaluation(request.getCharacterId(), userId));
    }

    /**
     * 为当前用户的评测构造等待审核的案例草稿。
     *
     * @param evaluationId 独立评测主键
     * @param request 数据集版本与数量
     * @param authentication 当前认证信息
     * @return 新建草稿案例
     */
    @PostMapping("/{evaluationId}/cases")
    public ApiResponse<List<RoleEvaluationCase>> generateCases(
            @PathVariable Long evaluationId,
            @Valid @RequestBody CreateRoleEvaluationCasesRequest request,
            Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.generateDraftCases(
                evaluationId, request.getDatasetVersion(), request.getLimit()));
    }

    /**
     * 查询独立评测内的案例。
     *
     * @param evaluationId 独立评测主键
     * @param datasetVersion 可选数据集版本
     * @param authentication 当前认证信息
     * @return 案例列表
     */
    @GetMapping("/{evaluationId}/cases")
    public ApiResponse<List<RoleEvaluationCase>> listCases(
            @PathVariable Long evaluationId,
            @RequestParam(required = false) String datasetVersion,
            Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.listCases(evaluationId, datasetVersion));
    }

    /**
     * 查询独立评测在指定数据集版本下的汇总报告。
     *
     * @param evaluationId 独立评测主键
     * @param datasetVersion 数据集版本
     * @param authentication 当前认证信息
     * @return 评测汇总报告
     */
    @GetMapping("/{evaluationId}/report")
    public ApiResponse<RoleEvaluationReportResponse> report(
            @PathVariable Long evaluationId,
            @RequestParam String datasetVersion,
            Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.report(evaluationId, datasetVersion));
    }

    /**
     * 查询当前用户在该公共角色上的个人历史版本，供选择优化基线。
     *
     * @param evaluationId 独立评测主键
     * @param authentication 当前认证信息
     * @return 按版本号倒序排列的个人角色版本
     */
    @GetMapping("/{evaluationId}/role-versions")
    public ApiResponse<List<UserRoleVersion>> listRoleVersions(
            @PathVariable Long evaluationId, Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.listRoleVersions(evaluationId));
    }

    /**
     * 审核案例，使其可以参与真实评测运行。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @param authentication 当前认证信息
     * @return 已审核案例
     */
    @PostMapping("/{evaluationId}/cases/{caseId}/approve")
    public ApiResponse<RoleEvaluationCase> approveCase(
            @PathVariable Long evaluationId, @PathVariable Long caseId, Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.approveCase(evaluationId, caseId));
    }

    /**
     * 拒绝一个不适合作为基线的案例草稿。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @param authentication 当前认证信息
     * @return 已拒绝案例
     */
    @PostMapping("/{evaluationId}/cases/{caseId}/reject")
    public ApiResponse<RoleEvaluationCase> rejectCase(
            @PathVariable Long evaluationId, @PathVariable Long caseId, Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.rejectCase(evaluationId, caseId));
    }

    /**
     * 运行一个已审核案例，并保存角色回复与 Judge 评分。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @param authentication 当前认证信息
     * @return 新建运行记录
     */
    @PostMapping("/{evaluationId}/cases/{caseId}/runs")
    public ApiResponse<RoleEvaluationRun> runCase(
            @PathVariable Long evaluationId, @PathVariable Long caseId, Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.runCase(evaluationId, caseId));
    }

    /**
     * 查询一个案例在当前独立评测中的运行历史。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @param authentication 当前认证信息
     * @return 运行记录列表
     */
    @GetMapping("/{evaluationId}/cases/{caseId}/runs")
    public ApiResponse<List<RoleEvaluationRun>> listRuns(
            @PathVariable Long evaluationId, @PathVariable Long caseId, Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.listRuns(evaluationId, caseId));
    }

    /**
     * 汇总同一角色版本的多条低分运行，生成有限规则建议的草稿批次。
     *
     * @param evaluationId 独立评测主键
     * @param request 用户选择的低分运行
     * @param authentication 当前认证信息
     * @return 新建规则改进批次
     */
    @PostMapping("/{evaluationId}/rule-improvement-batches")
    public ApiResponse<RoleEvaluationImprovementBatch> createImprovementBatch(
            @PathVariable Long evaluationId,
            @Valid @RequestBody CreateRoleEvaluationImprovementBatchRequest request,
            Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.createImprovementBatch(evaluationId, request.getRunIds()));
    }

    /**
     * 查询当前评测下的规则改进批次。
     *
     * @param evaluationId 独立评测主键
     * @param authentication 当前认证信息
     * @return 改进批次列表
     */
    @GetMapping("/{evaluationId}/rule-improvement-batches")
    public ApiResponse<List<RoleEvaluationImprovementBatch>> listImprovementBatches(
            @PathVariable Long evaluationId, Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.listImprovementBatches(evaluationId));
    }

    /**
     * 查询一个改进批次生成的全部规则建议及其审核状态。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @param authentication 当前认证信息
     * @return 规则建议列表
     */
    @GetMapping("/{evaluationId}/rule-improvement-batches/{batchId}/rule-improvements")
    public ApiResponse<List<RoleEvaluationRuleImprovement>> listBatchImprovements(
            @PathVariable Long evaluationId, @PathVariable Long batchId, Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.listBatchImprovements(evaluationId, batchId));
    }

    /**
     * 将用户确认的有限规则建议一次性写入一个新的个人角色版本。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @param request 选择的建议及可选历史版本基线
     * @param authentication 当前认证信息
     * @return 已应用的改进批次
     */
    @PostMapping("/{evaluationId}/rule-improvement-batches/{batchId}/apply")
    public ApiResponse<RoleEvaluationImprovementBatch> applyImprovementBatch(
            @PathVariable Long evaluationId,
            @PathVariable Long batchId,
            @Valid @RequestBody ApplyRoleEvaluationImprovementBatchRequest request,
            Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.applyImprovementBatch(
                evaluationId, batchId, request.getImprovementIds(), request.getBaseUserRoleVersionId()));
    }

    /**
     * 拒绝整个改进批次及其中尚未处理的规则建议。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @param authentication 当前认证信息
     * @return 已拒绝批次
     */
    @PostMapping("/{evaluationId}/rule-improvement-batches/{batchId}/reject")
    public ApiResponse<RoleEvaluationImprovementBatch> rejectImprovementBatch(
            @PathVariable Long evaluationId, @PathVariable Long batchId, Authentication authentication) {
        requireOwnedEvaluation(evaluationId, authentication);
        return ApiResponse.success(evaluationService.rejectImprovementBatch(evaluationId, batchId));
    }

    /**
     * 从认证信息中取用户主键，并校验其拥有当前独立评测。
     *
     * @param evaluationId 独立评测主键
     * @param authentication 当前认证信息
     */
    private void requireOwnedEvaluation(Long evaluationId, Authentication authentication) {
        evaluationService.requireOwnedEvaluation(evaluationId, jwtUserIdExtractor.requireUserId(authentication));
    }
}
