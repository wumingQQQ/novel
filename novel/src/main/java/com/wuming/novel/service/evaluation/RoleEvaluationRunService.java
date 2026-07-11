package com.wuming.novel.service.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.novel.domain.dto.RoleEvaluationReportResponse;
import com.wuming.novel.domain.entity.RoleEvaluation;
import com.wuming.novel.domain.entity.RoleEvaluationCase;
import com.wuming.novel.domain.entity.RoleEvaluationRun;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理角色评测运行记录、运行执行和结果汇总。
 */
@Service
@RequiredArgsConstructor
public class RoleEvaluationRunService {
    private static final String APPROVED = "APPROVED";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String INVALID = "INVALID";
    private static final String FAILED = "FAILED";

    private final RoleEvaluationRunMapper runMapper;
    private final RoleEvaluationCaseService caseService;
    private final RoleEvaluationRunner runner;
    private final ObjectMapper objectMapper;

    @Value("${llm.deepseek.model:unknown}")
    private String model;

    @Value("${novel.role-evaluation.prompt-version:v1}")
    private String promptVersion;

    @Value("${novel.role-evaluation.role-example-top-k:10}")
    private int roleExampleTopK;

    @Value("${novel.role-evaluation.role-example-top-n:3}")
    private int roleExampleTopN;

    /**
     * 查询一个评测案例的全部运行历史。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @return 按主键倒序排列的运行记录
     */
    public List<RoleEvaluationRun> listRuns(Long evaluationId, Long caseId) {
        caseService.requireEvaluationCase(evaluationId, caseId);
        return runMapper.selectList(new LambdaQueryWrapper<RoleEvaluationRun>()
                .eq(RoleEvaluationRun::getEvaluationId, evaluationId)
                .eq(RoleEvaluationRun::getCaseId, caseId)
                .orderByDesc(RoleEvaluationRun::getId));
    }

    /**
     * 执行一个已审核案例，并保存生成回复、召回证据和 Judge 评分。
     *
     * @param evaluation 当前独立评测
     * @param caseId 评测案例主键
     * @return 新建的运行记录
     */
    public RoleEvaluationRun runCase(RoleEvaluation evaluation, Long caseId) {
        RoleEvaluationCase evaluationCase = caseService.requireEvaluationCase(evaluation.getId(), caseId);
        if (!APPROVED.equals(evaluationCase.getStatus())) {
            throw new IllegalStateException("只有已审核的评测案例可以运行");
        }
        RoleEvaluationRun run = new RoleEvaluationRun();
        run.setEvaluationId(evaluation.getId());
        run.setCaseId(caseId);
        run.setUserRoleVersionId(evaluation.getUserRoleVersionId());
        run.setStatus(RUNNING);
        run.setConfigSnapshot(buildConfigSnapshot(evaluation, evaluationCase));
        runMapper.insert(run);
        try {
            runner.run(evaluation, evaluationCase, run);
            runMapper.updateById(run);
        } catch (RuntimeException e) {
            run.setStatus(FAILED);
            run.setFailureReason(trimToNull(e.getMessage()));
            runMapper.updateById(run);
            throw e;
        }
        return run;
    }

    /**
     * 汇总指定独立评测、数据集版本下每个案例的最新运行结果。
     *
     * @param evaluationId 独立评测主键
     * @param datasetVersion 数据集版本
     * @return 评测汇总报告
     */
    public RoleEvaluationReportResponse report(Long evaluationId, String datasetVersion) {
        List<RoleEvaluationCase> cases = caseService.listCases(evaluationId, datasetVersion);
        RoleEvaluationReportResponse report = new RoleEvaluationReportResponse();
        report.setDatasetVersion(datasetVersion);
        report.setCaseCount(cases.size());
        report.setApprovedCaseCount((int) cases.stream().filter(item -> APPROVED.equals(item.getStatus())).count());
        List<Long> caseIds = cases.stream().map(RoleEvaluationCase::getId).toList();
        List<RoleEvaluationRun> latestRuns = findLatestRuns(caseIds);
        report.setSucceededRunCount((int) latestRuns.stream().filter(item -> SUCCEEDED.equals(item.getStatus())).count());
        report.setInvalidRunCount((int) latestRuns.stream().filter(item -> INVALID.equals(item.getStatus())).count());
        report.setFailedRunCount((int) latestRuns.stream().filter(item -> FAILED.equals(item.getStatus())).count());
        java.util.OptionalDouble average = latestRuns.stream().filter(item -> SUCCEEDED.equals(item.getStatus()))
                .map(RoleEvaluationRun::getTotalScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
        report.setAverageTotalScore(average.isPresent() ? average.getAsDouble() : null);
        return report;
    }

    /**
     * 查询运行记录。
     *
     * @param runId 运行主键
     * @return 运行记录
     */
    public RoleEvaluationRun requireRun(Long runId) {
        RoleEvaluationRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new IllegalArgumentException("评测运行不存在: " + runId);
        }
        return run;
    }

    /**
     * 查询并校验运行记录属于指定独立评测。
     *
     * @param evaluationId 独立评测主键
     * @param runId 评测运行主键
     * @return 归属正确的运行记录
     */
    public RoleEvaluationRun requireEvaluationRun(Long evaluationId, Long runId) {
        RoleEvaluationRun run = requireRun(runId);
        if (!Objects.equals(evaluationId, run.getEvaluationId())) {
            throw new IllegalStateException("评测运行不属于当前角色评测");
        }
        return run;
    }

    /**
     * 构造完整运行配置快照，便于之后复盘模型、提示词和召回参数。
     *
     * @param evaluation 当前独立评测
     * @param evaluationCase 当前案例
     * @return JSON 格式的配置快照
     */
    private String buildConfigSnapshot(RoleEvaluation evaluation, RoleEvaluationCase evaluationCase) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mode", "novel-internal");
        snapshot.put("excludedPassageId", evaluationCase.getPassageId());
        snapshot.put("model", model);
        snapshot.put("promptVersion", promptVersion);
        snapshot.put("roleExampleTopK", roleExampleTopK);
        snapshot.put("roleExampleTopN", roleExampleTopN);
        snapshot.put("userRoleVersionId", evaluation.getUserRoleVersionId());
        return toJson(snapshot);
    }

    /**
     * 查询每个案例的最新一条运行记录。
     *
     * @param caseIds 案例主键集合
     * @return 每个案例最多一条的最新运行记录
     */
    private List<RoleEvaluationRun> findLatestRuns(List<Long> caseIds) {
        if (caseIds.isEmpty()) {
            return List.of();
        }
        return runMapper.selectList(new LambdaQueryWrapper<RoleEvaluationRun>()
                        .in(RoleEvaluationRun::getCaseId, caseIds)
                        .orderByDesc(RoleEvaluationRun::getId))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        RoleEvaluationRun::getCaseId,
                        item -> item,
                        (first, ignored) -> first,
                        LinkedHashMap::new))
                .values().stream().toList();
    }

    /**
     * 将对象序列化为运行配置快照使用的 JSON 文本。
     *
     * @param value 待序列化对象
     * @return JSON 文本
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("评测审计数据序列化失败", e);
        }
    }

    /**
     * 将空白失败原因规整为 null。
     *
     * @param value 异常消息
     * @return 规整后的失败原因
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
