package com.wuming.novel.service.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.dto.RolePublicPreview;
import com.wuming.novel.domain.dto.RoleWorkspaceDetailResponse;
import com.wuming.novel.domain.dto.RoleWorkspaceEvaluationSummary;
import com.wuming.novel.domain.dto.RoleWorkspaceSummary;
import com.wuming.novel.domain.entity.RoleEvaluation;
import com.wuming.novel.domain.entity.RoleEvaluationCase;
import com.wuming.novel.domain.entity.RoleEvaluationImprovementBatch;
import com.wuming.novel.domain.entity.RoleEvaluationRun;
import com.wuming.novel.domain.entity.UserRoleTrack;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationCaseMapper;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationImprovementBatchMapper;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationMapper;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationRunMapper;
import com.wuming.novel.infrastructure.mapper.UserRoleTrackMapper;
import com.wuming.novel.service.publicrole.RolePublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 聚合当前用户在公共角色上的评测与个人版本工作区。
 */
@Service
@RequiredArgsConstructor
public class RoleWorkspaceService {
    private static final String APPROVED = "APPROVED";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String DRAFT = "DRAFT";

    private final RoleEvaluationMapper evaluationMapper;
    private final RoleEvaluationCaseMapper caseMapper;
    private final RoleEvaluationRunMapper runMapper;
    private final RoleEvaluationImprovementBatchMapper batchMapper;
    private final UserRoleTrackMapper trackMapper;
    private final RolePublicService rolePublicService;

    /**
     * 按公共角色聚合当前用户创建过的独立评测。
     *
     * @param userId 当前认证用户主键
     * @return 按最近评测时间倒序排列的工作区摘要
     */
    public List<RoleWorkspaceSummary> listWorkspaces(Long userId) {
        requireUserId(userId);
        Map<Long, List<RoleEvaluation>> evaluationsByCharacter = evaluationsForUser(userId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        RoleEvaluation::getCharacterId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return evaluationsByCharacter.values().stream()
                .map(evaluations -> toWorkspaceSummary(userId, evaluations))
                .sorted(Comparator.comparing(RoleWorkspaceSummary::latestEvaluationTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 查询当前用户在一个公共角色下的评测工作区与各轮评测概览。
     *
     * @param userId 当前认证用户主键
     * @param characterId 公共角色主键
     * @return 当前用户专属的角色工作区详情
     */
    public RoleWorkspaceDetailResponse getWorkspace(Long userId, Long characterId) {
        requireUserId(userId);
        List<RoleEvaluation> evaluations = evaluationsForUser(userId).stream()
                .filter(evaluation -> Objects.equals(evaluation.getCharacterId(), characterId))
                .toList();
        if (evaluations.isEmpty()) {
            throw new IllegalArgumentException("我的角色工作区不存在: " + characterId);
        }
        RoleWorkspaceSummary summary = toWorkspaceSummary(userId, evaluations);
        List<RoleWorkspaceEvaluationSummary> evaluationSummaries = evaluations.stream()
                .sorted(Comparator.comparing(RoleEvaluation::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toEvaluationSummary)
                .toList();
        return new RoleWorkspaceDetailResponse(summary, evaluationSummaries);
    }

    /**
     * 查询当前用户的所有评测，并以创建时间倒序提供给工作区聚合。
     */
    private List<RoleEvaluation> evaluationsForUser(Long userId) {
        return evaluationMapper.selectList(new LambdaQueryWrapper<RoleEvaluation>()
                .eq(RoleEvaluation::getUserId, userId)
                .orderByDesc(RoleEvaluation::getCreateTime)
                .orderByDesc(RoleEvaluation::getId));
    }

    /**
     * 将同一角色的多条评测记录压缩为一个工作区摘要。
     */
    private RoleWorkspaceSummary toWorkspaceSummary(Long userId, List<RoleEvaluation> evaluations) {
        RoleEvaluation latestEvaluation = evaluations.stream()
                .max(Comparator.comparing(RoleEvaluation::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow();
        UserRoleTrack track = trackMapper.selectOne(new LambdaQueryWrapper<UserRoleTrack>()
                .eq(UserRoleTrack::getUserId, userId)
                .eq(UserRoleTrack::getCharacterId, latestEvaluation.getCharacterId()));
        RolePublicPreview character = rolePublicService.getPreview(latestEvaluation.getCharacterId());
        return new RoleWorkspaceSummary(
                character,
                evaluations.size(),
                latestEvaluation.getId(),
                track == null ? null : track.getId(),
                track == null ? null : track.getLatestVersionId(),
                track == null ? null : track.getLatestVersionNo(),
                latestEvaluation.getCreateTime());
    }

    /**
     * 汇总单次评测的案例、运行及待处理改进批次计数。
     */
    private RoleWorkspaceEvaluationSummary toEvaluationSummary(RoleEvaluation evaluation) {
        long caseCount = countCases(evaluation.getId(), null);
        long approvedCaseCount = countCases(evaluation.getId(), APPROVED);
        List<RoleEvaluationRun> successfulRuns = runMapper.selectList(new LambdaQueryWrapper<RoleEvaluationRun>()
                .eq(RoleEvaluationRun::getEvaluationId, evaluation.getId())
                .eq(RoleEvaluationRun::getStatus, SUCCEEDED)
                .orderByDesc(RoleEvaluationRun::getCreateTime));
        Double latestScore = successfulRuns.stream()
                .map(RoleEvaluationRun::getTotalScore)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        long draftBatchCount = batchMapper.selectCount(new LambdaQueryWrapper<RoleEvaluationImprovementBatch>()
                .eq(RoleEvaluationImprovementBatch::getEvaluationId, evaluation.getId())
                .eq(RoleEvaluationImprovementBatch::getStatus, DRAFT));
        return new RoleWorkspaceEvaluationSummary(
                evaluation.getId(), evaluation.getUserRoleVersionId(), caseCount, approvedCaseCount,
                successfulRuns.size(), draftBatchCount, latestScore, evaluation.getCreateTime());
    }

    /**
     * 统计一轮评测中可选状态的案例数量。
     */
    private long countCases(Long evaluationId, String status) {
        LambdaQueryWrapper<RoleEvaluationCase> query = new LambdaQueryWrapper<RoleEvaluationCase>()
                .eq(RoleEvaluationCase::getEvaluationId, evaluationId);
        if (status != null) {
            query.eq(RoleEvaluationCase::getStatus, status);
        }
        return caseMapper.selectCount(query);
    }

    /**
     * 校验工作区聚合始终基于认证用户。
     */
    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
    }
}
