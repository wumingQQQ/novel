package com.wuming.novel.service.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.dto.RoleEvaluationImprovementBatchResult;
import com.wuming.novel.domain.entity.RoleEvaluation;
import com.wuming.novel.domain.entity.RoleEvaluationCase;
import com.wuming.novel.domain.entity.RoleEvaluationImprovementBatch;
import com.wuming.novel.domain.entity.RoleEvaluationRuleImprovement;
import com.wuming.novel.domain.entity.RoleEvaluationRuleImprovementRun;
import com.wuming.novel.domain.entity.RoleEvaluationRun;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.domain.entity.UserRoleReactionRule;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationImprovementBatchMapper;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationMapper;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationRuleImprovementMapper;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationRuleImprovementRunMapper;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationRunMapper;
import com.wuming.novel.infrastructure.mapper.RoleReactionRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 管理多条低分评测运行汇总得到的有限规则改进批次及其个人版本应用。
 */
@Service
@RequiredArgsConstructor
public class RoleEvaluationImprovementService {
    private static final String DRAFT = "DRAFT";
    private static final String APPLIED = "APPLIED";

    private final RoleEvaluationImprovementBatchMapper batchMapper;
    private final RoleEvaluationRuleImprovementMapper improvementMapper;
    private final RoleEvaluationRuleImprovementRunMapper improvementRunMapper;
    private final RoleEvaluationRunMapper runMapper;
    private final RoleEvaluationMapper evaluationMapper;
    private final RoleReactionRuleMapper publicRuleMapper;
    private final RoleEvaluationCaseService caseService;
    private final UserRoleVersionService versionService;
    private final UserRoleReactionRuleService ruleService;
    private final RoleEvaluationRunner runner;

    @Value("${novel.role-evaluation.rule-improvement-threshold:3.0}")
    private double scoreThreshold;

    @Value("${novel.role-evaluation.rule-improvement-max-runs:12}")
    private int maxRuns;

    @Value("${novel.role-evaluation.rule-improvement-max-changes:2}")
    private int maxChanges;

    /**
     * 汇总同一角色版本的多条低分运行，生成一个包含有限规则建议的草稿批次。
     *
     * @param evaluation 当前独立评测
     * @param runIds 用户选定的低分运行主键
     * @return 已保存的改进批次
     */
    @Transactional
    public RoleEvaluationImprovementBatch createBatch(RoleEvaluation evaluation, List<Long> runIds) {
        List<RoleEvaluationRun> runs = requireEligibleRuns(evaluation, runIds);
        Long evaluatedVersionId = runs.getFirst().getUserRoleVersionId();
        List<RoleReactionRule> rules = ruleService.loadEffectiveRules(
                evaluation.getCharacterId(), evaluatedVersionId);
        if (rules.isEmpty()) {
            throw new IllegalStateException("角色没有可改进的反应规则");
        }
        List<RoleEvaluationRunner.ImprovementRunEvidence> evidence = runs.stream()
                .map(run -> new RoleEvaluationRunner.ImprovementRunEvidence(
                        run, caseService.requireEvaluationCase(evaluation.getId(), run.getCaseId())))
                .toList();
        int allowedChanges = Math.max(1, maxChanges);
        RoleEvaluationImprovementBatchResult result = runner.buildImprovementBatch(evidence, rules, allowedChanges);
        validateBatchResult(result, rules, runs, allowedChanges);

        RoleEvaluationImprovementBatch batch = new RoleEvaluationImprovementBatch();
        batch.setEvaluationId(evaluation.getId());
        batch.setUserRoleVersionId(evaluatedVersionId);
        batch.setRunCount(runs.size());
        batch.setMaxChanges(allowedChanges);
        batch.setSummary(trimToNull(result.summary()));
        batch.setStatus(DRAFT);
        batchMapper.insert(batch);
        if (Boolean.TRUE.equals(result.shouldImprove())) {
            result.improvements().forEach(proposal -> insertImprovement(batch, evaluation, rules, proposal));
        }
        return batch;
    }

    /**
     * 查询一个改进批次下的全部规则建议。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @return 按主键正序排列的建议
     */
    public List<RoleEvaluationRuleImprovement> listImprovements(Long evaluationId, Long batchId) {
        requireBatch(evaluationId, batchId);
        List<RoleEvaluationRuleImprovement> improvements = improvementMapper.selectList(new LambdaQueryWrapper<RoleEvaluationRuleImprovement>()
                .eq(RoleEvaluationRuleImprovement::getBatchId, batchId)
                .orderByAsc(RoleEvaluationRuleImprovement::getId));
        improvements.forEach(this::fillEvidenceRunIds);
        return improvements;
    }

    /**
     * 查询评测下全部改进批次。
     *
     * @param evaluationId 独立评测主键
     * @return 按主键倒序排列的批次列表
     */
    public List<RoleEvaluationImprovementBatch> listBatches(Long evaluationId) {
        return batchMapper.selectList(new LambdaQueryWrapper<RoleEvaluationImprovementBatch>()
                .eq(RoleEvaluationImprovementBatch::getEvaluationId, evaluationId)
                .orderByDesc(RoleEvaluationImprovementBatch::getId));
    }

    /**
     * 将用户确认的有限条建议一次性应用为一个新的个人角色版本。
     *
     * @param evaluation 当前独立评测
     * @param batchId 改进批次主键
     * @param improvementIds 用户确认的建议主键
     * @param requestedBaseVersionId 可选历史基础版本主键
     * @return 已应用的改进批次
     */
    @Transactional
    public RoleEvaluationImprovementBatch applyBatch(RoleEvaluation evaluation,
                                                      Long batchId,
                                                      List<Long> improvementIds,
                                                      Long requestedBaseVersionId) {
        RoleEvaluationImprovementBatch batch = requireBatch(evaluation.getId(), batchId);
        if (!DRAFT.equals(batch.getStatus())) {
            throw new IllegalStateException("只有草稿状态的规则改进批次可以应用");
        }
        List<RoleEvaluationRuleImprovement> selected = requireSelectedImprovements(batch, improvementIds);
        List<UserRoleReactionRuleService.RuleChange> changes = selected.stream()
                .map(this::toRuleChange)
                .toList();
        UserRoleVersionService.BatchVersionApplication application = versionService.applyBatch(
                evaluation, batch, changes, requestedBaseVersionId);
        evaluation.setUserRoleTrackId(application.track().getId());
        evaluation.setUserRoleVersionId(application.version().getId());
        evaluationMapper.updateById(evaluation);

        Map<Long, UserRoleReactionRule> appliedRules = application.rules().stream().collect(java.util.stream.Collectors.toMap(
                UserRoleReactionRule::getSourceRuleId, item -> item));
        markSelectedApplied(selected, application, appliedRules);
        rejectUnselectedDraftImprovements(batch.getId(), selected.stream().map(RoleEvaluationRuleImprovement::getId).toList());
        batch.setStatus(APPLIED);
        batch.setReviewedTime(LocalDateTime.now());
        batchMapper.updateById(batch);
        return batch;
    }

    /**
     * 拒绝整个改进批次及其中尚未处理的规则建议。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @return 已拒绝批次
     */
    @Transactional
    public RoleEvaluationImprovementBatch rejectBatch(Long evaluationId, Long batchId) {
        RoleEvaluationImprovementBatch batch = requireBatch(evaluationId, batchId);
        if (!DRAFT.equals(batch.getStatus())) {
            throw new IllegalStateException("只有草稿状态的规则改进批次可以拒绝");
        }
        rejectUnselectedDraftImprovements(batch.getId(), List.of());
        batch.setStatus("REJECTED");
        batch.setReviewedTime(LocalDateTime.now());
        batchMapper.updateById(batch);
        return batch;
    }

    /**
     * 查询并校验改进批次属于指定独立评测。
     *
     * @param evaluationId 独立评测主键
     * @param batchId 改进批次主键
     * @return 归属正确的改进批次
     */
    public RoleEvaluationImprovementBatch requireBatch(Long evaluationId, Long batchId) {
        RoleEvaluationImprovementBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("规则改进批次不存在: " + batchId);
        }
        if (!Objects.equals(evaluationId, batch.getEvaluationId())) {
            throw new IllegalStateException("规则改进批次不属于当前角色评测");
        }
        return batch;
    }

    /**
     * 校验所选运行均为当前评测、同一角色版本下的低分成功运行，且案例不重复。
     *
     * @param evaluation 当前独立评测
     * @param runIds 用户选择的运行主键
     * @return 按用户选择顺序排列的合格运行
     */
    private List<RoleEvaluationRun> requireEligibleRuns(RoleEvaluation evaluation, List<Long> runIds) {
        if (runIds == null || runIds.size() < 2 || runIds.size() > Math.max(2, maxRuns)) {
            throw new IllegalArgumentException("改进批次需要选择2到" + Math.max(2, maxRuns) + "条运行记录");
        }
        Set<Long> uniqueRunIds = new HashSet<>(runIds);
        if (uniqueRunIds.size() != runIds.size()) {
            throw new IllegalArgumentException("改进批次不能重复选择同一运行记录");
        }
        List<RoleEvaluationRun> runs = runIds.stream().map(runMapper::selectById).toList();
        if (runs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("改进批次包含不存在的评测运行");
        }
        Long versionId = runs.getFirst().getUserRoleVersionId();
        Set<Long> caseIds = new HashSet<>();
        for (RoleEvaluationRun run : runs) {
            if (!Objects.equals(evaluation.getId(), run.getEvaluationId())) {
                throw new IllegalStateException("评测运行不属于当前角色评测");
            }
            if (!"SUCCEEDED".equals(run.getStatus()) || run.getTotalScore() == null
                    || run.getTotalScore() >= scoreThreshold) {
                throw new IllegalStateException("改进批次只能选择低于阈值的成功评测运行");
            }
            if (!Objects.equals(versionId, run.getUserRoleVersionId())) {
                throw new IllegalStateException("改进批次中的运行必须使用同一个角色版本");
            }
            if (!caseIds.add(run.getCaseId())) {
                throw new IllegalStateException("改进批次不能选择同一案例的多次运行");
            }
        }
        return runs;
    }

    /**
     * 校验汇总模型结果只对当前规则和选定证据作出有限、可审计的建议。
     *
     * @param result 模型输出
     * @param rules 当前有效规则
     * @param runs 用户选择的运行
     * @param allowedChanges 最大建议数量
     */
    private void validateBatchResult(RoleEvaluationImprovementBatchResult result,
                                     List<RoleReactionRule> rules,
                                     List<RoleEvaluationRun> runs,
                                     int allowedChanges) {
        if (result == null || isBlank(result.summary())) {
            throw new IllegalStateException("改进批次缺少汇总结论");
        }
        if (!Boolean.TRUE.equals(result.shouldImprove())) {
            if (result.improvements() != null && !result.improvements().isEmpty()) {
                throw new IllegalStateException("改进批次结论与规则建议不一致");
            }
            return;
        }
        if (result.improvements() == null || result.improvements().isEmpty()
                || result.improvements().size() > allowedChanges) {
            throw new IllegalStateException("改进批次建议数量不合法");
        }
        Set<Long> validRuleIds = rules.stream().map(RoleReactionRule::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> validRunIds = runs.stream().map(RoleEvaluationRun::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> changedRuleIds = new HashSet<>();
        for (RoleEvaluationImprovementBatchResult.Proposal proposal : result.improvements()) {
            if (proposal == null || !validRuleIds.contains(proposal.ruleId())
                    || !changedRuleIds.add(proposal.ruleId()) || isBlank(proposal.proposedRule())
                    || isBlank(proposal.rationale()) || proposal.evidenceRunIds() == null) {
                throw new IllegalStateException("改进批次包含无效规则建议");
            }
            Set<Long> evidenceIds = new HashSet<>(proposal.evidenceRunIds());
            if (evidenceIds.size() < 2 || !validRunIds.containsAll(evidenceIds)) {
                throw new IllegalStateException("每条规则建议必须引用至少两条选定运行证据");
            }
        }
    }

    /**
     * 将一条模型建议与其全部运行证据持久化。
     *
     * @param batch 所属改进批次
     * @param evaluation 当前独立评测
     * @param rules 当前有效规则
     * @param proposal 模型建议
     */
    private void insertImprovement(RoleEvaluationImprovementBatch batch,
                                   RoleEvaluation evaluation,
                                   List<RoleReactionRule> rules,
                                   RoleEvaluationImprovementBatchResult.Proposal proposal) {
        RoleReactionRule targetRule = rules.stream()
                .filter(rule -> Objects.equals(rule.getId(), proposal.ruleId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("改进建议未选择当前角色的有效规则"));
        RoleEvaluationRuleImprovement improvement = new RoleEvaluationRuleImprovement();
        improvement.setEvaluationId(evaluation.getId());
        improvement.setBatchId(batch.getId());
        improvement.setRunId(proposal.evidenceRunIds().getFirst());
        improvement.setCharacterId(evaluation.getCharacterId());
        improvement.setRuleId(targetRule.getId());
        improvement.setSituation(targetRule.getSituation());
        improvement.setOriginalRule(targetRule.getRule());
        improvement.setProposedRule(proposal.proposedRule().trim());
        improvement.setRationale(proposal.rationale().trim());
        improvement.setStatus(DRAFT);
        improvementMapper.insert(improvement);
        proposal.evidenceRunIds().stream().distinct().forEach(runId -> {
            RoleEvaluationRuleImprovementRun evidence = new RoleEvaluationRuleImprovementRun();
            evidence.setImprovementId(improvement.getId());
            evidence.setRunId(runId);
            improvementRunMapper.insert(evidence);
        });
    }

    /**
     * 将关联表中的全部运行证据填充到建议响应，供用户审核模型引用。
     *
     * @param improvement 待填充证据的规则建议
     */
    private void fillEvidenceRunIds(RoleEvaluationRuleImprovement improvement) {
        List<Long> evidenceRunIds = improvementRunMapper.selectList(
                        new LambdaQueryWrapper<RoleEvaluationRuleImprovementRun>()
                                .eq(RoleEvaluationRuleImprovementRun::getImprovementId, improvement.getId())
                                .orderByAsc(RoleEvaluationRuleImprovementRun::getId))
                .stream()
                .map(RoleEvaluationRuleImprovementRun::getRunId)
                .toList();
        improvement.setEvidenceRunIds(evidenceRunIds);
    }

    /**
     * 校验用户选择的建议均属于草稿批次，且不超过批次修改上限。
     *
     * @param batch 目标改进批次
     * @param improvementIds 用户选择的建议主键
     * @return 用户确认应用的建议
     */
    private List<RoleEvaluationRuleImprovement> requireSelectedImprovements(RoleEvaluationImprovementBatch batch,
                                                                             List<Long> improvementIds) {
        if (improvementIds == null || improvementIds.isEmpty()
                || improvementIds.size() > batch.getMaxChanges()) {
            throw new IllegalArgumentException("应用建议数量必须在1到" + batch.getMaxChanges() + "之间");
        }
        if (new HashSet<>(improvementIds).size() != improvementIds.size()) {
            throw new IllegalArgumentException("不能重复选择同一规则建议");
        }
        List<RoleEvaluationRuleImprovement> selected = improvementIds.stream()
                .map(improvementMapper::selectById)
                .toList();
        if (selected.stream().anyMatch(Objects::isNull)
                || selected.stream().anyMatch(item -> !Objects.equals(batch.getId(), item.getBatchId())
                || !DRAFT.equals(item.getStatus()))) {
            throw new IllegalStateException("只能应用当前草稿批次中的规则建议");
        }
        return selected;
    }

    /**
     * 将一条待应用建议转换为新版本规则快照所需的来源规则与新文本。
     *
     * @param improvement 用户确认的规则建议
     * @return 规则快照改动
     */
    private UserRoleReactionRuleService.RuleChange toRuleChange(RoleEvaluationRuleImprovement improvement) {
        RoleReactionRule publicRule = publicRuleMapper.selectById(improvement.getRuleId());
        if (publicRule == null || !Objects.equals(publicRule.getCharacterId(), improvement.getCharacterId())) {
            throw new IllegalStateException("待改进的反应规则不存在或不属于目标角色");
        }
        return new UserRoleReactionRuleService.RuleChange(publicRule, improvement.getProposedRule());
    }

    /**
     * 回写已应用建议对应的新版本和个人规则快照。
     *
     * @param selected 用户确认的建议
     * @param application 新版本应用结果
     * @param appliedRules 按公共来源规则主键索引的个人规则
     */
    private void markSelectedApplied(List<RoleEvaluationRuleImprovement> selected,
                                     UserRoleVersionService.BatchVersionApplication application,
                                     Map<Long, UserRoleReactionRule> appliedRules) {
        LocalDateTime reviewedTime = LocalDateTime.now();
        for (RoleEvaluationRuleImprovement improvement : selected) {
            UserRoleReactionRule rule = appliedRules.get(improvement.getRuleId());
            if (rule == null) {
                throw new IllegalStateException("新个人版本缺少已应用规则快照");
            }
            improvement.setStatus(APPLIED);
            improvement.setBaseUserRoleVersionId(application.baseVersionId());
            improvement.setUserRoleVersionId(application.version().getId());
            improvement.setUserRoleReactionRuleId(rule.getId());
            improvement.setReviewedTime(reviewedTime);
            improvementMapper.updateById(improvement);
        }
    }

    /**
     * 将批次中未被选中的草稿建议标记为拒绝，避免同一批次再次产生额外版本。
     *
     * @param batchId 改进批次主键
     * @param selectedIds 已应用建议主键
     */
    private void rejectUnselectedDraftImprovements(Long batchId, List<Long> selectedIds) {
        List<RoleEvaluationRuleImprovement> improvements = improvementMapper.selectList(
                new LambdaQueryWrapper<RoleEvaluationRuleImprovement>().eq(RoleEvaluationRuleImprovement::getBatchId, batchId));
        LocalDateTime reviewedTime = LocalDateTime.now();
        improvements.stream()
                .filter(item -> DRAFT.equals(item.getStatus()))
                .filter(item -> !selectedIds.contains(item.getId()))
                .forEach(item -> {
                    item.setStatus("REJECTED");
                    item.setReviewedTime(reviewedTime);
                    improvementMapper.updateById(item);
                });
    }

    /**
     * 将空白文本规整为 null。
     *
     * @param value 文本值
     * @return 规整结果
     */
    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /**
     * 判断文本是否为空白。
     *
     * @param value 文本值
     * @return 空白时返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
