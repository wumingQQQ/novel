package com.wuming.novel.service.evaluation;

import com.wuming.novel.domain.dto.RoleEvaluationReportResponse;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleEvaluation;
import com.wuming.novel.domain.entity.RoleEvaluationCase;
import com.wuming.novel.domain.entity.RoleEvaluationImprovementBatch;
import com.wuming.novel.domain.entity.RoleEvaluationRuleImprovement;
import com.wuming.novel.domain.entity.RoleEvaluationRun;
import com.wuming.novel.domain.entity.UserRoleTrack;
import com.wuming.novel.domain.entity.UserRoleVersion;
import com.wuming.novel.infrastructure.mapper.RoleCharacterMapper;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 角色评测的应用门面，负责评测归属校验以及案例、运行、建议服务的编排。
 */
@Service
@RequiredArgsConstructor
public class RoleEvaluationService {
    private final RoleCharacterMapper characterMapper;
    private final RoleEvaluationMapper evaluationMapper;
    private final UserRoleVersionService versionService;
    private final RoleEvaluationCaseService caseService;
    private final RoleEvaluationRunService runService;
    private final RoleEvaluationImprovementService improvementService;

    /**
     * 创建属于当前用户的独立角色评测。
     *
     * @param characterId 用户选定的公共角色主键
     * @param userId 当前用户主键
     * @return 新建评测
     */
    public RoleEvaluation createEvaluation(Long characterId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        requireCharacter(characterId);
        RoleEvaluation evaluation = new RoleEvaluation();
        evaluation.setUserId(userId);
        evaluation.setCharacterId(characterId);
        UserRoleTrack track = versionService.findTrack(userId, characterId);
        if (track != null) {
            // 同一用户仅保留一条演进轨迹；新评测默认使用最新个人版本，仍可在应用建议时指定历史版本。
            evaluation.setUserRoleTrackId(track.getId());
            evaluation.setUserRoleVersionId(track.getLatestVersionId());
        }
        evaluationMapper.insert(evaluation);
        return evaluation;
    }

    /**
     * 从评测目标角色的互动样本中构造等待人工审核的案例草稿。
     *
     * @param evaluationId 独立评测主键
     * @param datasetVersion 数据集版本
     * @param limit 最大构造数量
     * @return 新建的草稿案例列表
     */
    public List<RoleEvaluationCase> generateDraftCases(Long evaluationId, String datasetVersion, int limit) {
        RoleEvaluation evaluation = requireEvaluation(evaluationId);
        return caseService.generateDraftCases(
                evaluation, requireCharacter(evaluation.getCharacterId()), datasetVersion, limit);
    }

    /**
     * 查询并校验独立评测属于当前用户。
     *
     * @param evaluationId 独立评测主键
     * @param userId 当前用户主键
     * @return 当前用户可操作的评测
     */
    public RoleEvaluation requireOwnedEvaluation(Long evaluationId, Long userId) {
        RoleEvaluation evaluation = requireEvaluation(evaluationId);
        if (userId == null || !Objects.equals(evaluation.getUserId(), userId)) {
            throw new IllegalStateException("无权操作该角色评测");
        }
        return evaluation;
    }

    /**
     * 审核评测案例，使其可以参与真实评测运行。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @return 已审核案例
     */
    public RoleEvaluationCase approveCase(Long evaluationId, Long caseId) {
        return caseService.approveCase(evaluationId, caseId);
    }

    /**
     * 拒绝不适合作为基线的评测案例草稿。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @return 已拒绝案例
     */
    public RoleEvaluationCase rejectCase(Long evaluationId, Long caseId) {
        return caseService.rejectCase(evaluationId, caseId);
    }

    /**
     * 查询指定独立评测、数据集版本下的案例。
     *
     * @param evaluationId 独立评测主键
     * @param datasetVersion 数据集版本；为空时查询全部版本
     * @return 按主键倒序排列的案例列表
     */
    public List<RoleEvaluationCase> listCases(Long evaluationId, String datasetVersion) {
        return caseService.listCases(evaluationId, datasetVersion);
    }

    /**
     * 查询一个评测案例的全部运行历史。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @return 按主键倒序排列的运行记录
     */
    public List<RoleEvaluationRun> listRuns(Long evaluationId, Long caseId) {
        return runService.listRuns(evaluationId, caseId);
    }

    /**
     * 查询评测运行记录。
     *
     * @param runId 运行主键
     * @return 运行记录
     */
    public RoleEvaluationRun requireRun(Long runId) {
        return runService.requireRun(runId);
    }

    /**
     * 查询当前评测下的全部规则改进批次。
     *
     * @param evaluationId 独立评测主键
     * @return 按主键倒序排列的改进批次
     */
    public List<RoleEvaluationImprovementBatch> listImprovementBatches(Long evaluationId) {
        return improvementService.listBatches(evaluationId);
    }

    /**
     * 查询一个改进批次下的全部规则建议。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @return 按主键正序排列的规则建议
     */
    public List<RoleEvaluationRuleImprovement> listBatchImprovements(Long evaluationId, Long batchId) {
        return improvementService.listImprovements(evaluationId, batchId);
    }

    /**
     * 查询当前独立评测所属个人角色轨迹的全部历史版本。
     *
     * @param evaluationId 独立评测主键
     * @return 按版本号倒序排列的个人角色版本；尚未创建轨迹时返回空列表
     */
    public List<UserRoleVersion> listRoleVersions(Long evaluationId) {
        RoleEvaluation evaluation = requireEvaluation(evaluationId);
        if (evaluation.getUserRoleTrackId() == null) {
            return List.of();
        }
        return versionService.listVersions(evaluation.getUserRoleTrackId());
    }

    /**
     * 汇总指定独立评测和数据集版本下的运行结果。
     *
     * @param evaluationId 独立评测主键
     * @param datasetVersion 数据集版本
     * @return 评测汇总报告
     */
    public RoleEvaluationReportResponse report(Long evaluationId, String datasetVersion) {
        return runService.report(evaluationId, datasetVersion);
    }

    /**
     * 执行一个已审核案例，并保存生成回复、召回证据和 Judge 评分。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @return 新建的运行记录
     */
    public RoleEvaluationRun runCase(Long evaluationId, Long caseId) {
        return runService.runCase(requireEvaluation(evaluationId), caseId);
    }

    /**
     * 将多条同版本低分运行交给 LLM 汇总，创建一个有限规则建议的草稿批次。
     *
     * @param evaluationId 独立评测主键
     * @param runIds 用户选择的低分运行主键
     * @return 新建的改进批次
     */
    public RoleEvaluationImprovementBatch createImprovementBatch(Long evaluationId, List<Long> runIds) {
        return improvementService.createBatch(requireEvaluation(evaluationId), runIds);
    }

    /**
     * 将用户确认的有限规则建议一次性应用为一个新的个人角色版本。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @param improvementIds 用户确认应用的建议主键
     * @param baseUserRoleVersionId 可选历史基础版本主键
     * @return 已应用的改进批次
     */
    public RoleEvaluationImprovementBatch applyImprovementBatch(Long evaluationId,
                                                                 Long batchId,
                                                                 List<Long> improvementIds,
                                                                 Long baseUserRoleVersionId) {
        return improvementService.applyBatch(
                requireEvaluation(evaluationId), batchId, improvementIds, baseUserRoleVersionId);
    }

    /**
     * 拒绝整个规则改进批次及其中尚未处理的建议。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @return 已拒绝的改进批次
     */
    public RoleEvaluationImprovementBatch rejectImprovementBatch(Long evaluationId, Long batchId) {
        return improvementService.rejectBatch(evaluationId, batchId);
    }

    /**
     * 查询并校验案例属于指定独立评测。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @return 案例实体
     */
    public RoleEvaluationCase requireEvaluationCase(Long evaluationId, Long caseId) {
        return caseService.requireEvaluationCase(evaluationId, caseId);
    }

    /**
     * 查询并校验运行记录属于指定独立评测。
     *
     * @param evaluationId 独立评测主键
     * @param runId 评测运行主键
     * @return 运行记录
     */
    public RoleEvaluationRun requireEvaluationRun(Long evaluationId, Long runId) {
        return runService.requireEvaluationRun(evaluationId, runId);
    }

    /**
     * 查询并校验改进批次属于指定独立评测。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @return 改进批次实体
     */
    public RoleEvaluationImprovementBatch requireImprovementBatch(Long evaluationId, Long batchId) {
        return improvementService.requireBatch(evaluationId, batchId);
    }

    /**
     * 查询独立评测。
     *
     * @param evaluationId 独立评测主键
     * @return 评测实体
     */
    private RoleEvaluation requireEvaluation(Long evaluationId) {
        RoleEvaluation evaluation = evaluationMapper.selectById(evaluationId);
        if (evaluation == null) {
            throw new IllegalArgumentException("角色评测不存在: " + evaluationId);
        }
        return evaluation;
    }

    /**
     * 查询已完成构建的公共角色。
     *
     * @param characterId 角色主键
     * @return 已完成角色
     */
    private RoleCharacter requireCharacter(Long characterId) {
        RoleCharacter character = characterMapper.selectById(characterId);
        if (character == null) {
            throw new IllegalArgumentException("角色不存在: " + characterId);
        }
        if (!"COMPLETED".equals(character.getBuildStatus())) {
            throw new IllegalStateException("角色构建尚未完成");
        }
        return character;
    }
}
