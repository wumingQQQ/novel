package com.wuming.novel.service.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.novel.domain.dto.RoleEvaluationCaseDraftResult;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleEvaluation;
import com.wuming.novel.domain.entity.RoleEvaluationCase;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationCaseMapper;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理角色评测案例的构造、审核和查询。
 */
@Service
@RequiredArgsConstructor
public class RoleEvaluationCaseService {
    private static final String DRAFT = "DRAFT";
    private static final String APPROVED = "APPROVED";

    private final RoleEvaluationCaseMapper caseMapper;
    private final RoleEvaluationPassageSelectionService passageSelectionService;
    private final PromptTemplateRenderer renderer;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /**
     * 从角色互动样本按 Passage 去重构造等待人工审核的案例草稿。
     *
     * @param evaluation 目标独立评测
     * @param character 已完成构建的公共角色
     * @param datasetVersion 案例集版本
     * @param limit 最大构造数量
     * @return 新建的草稿案例列表
     */
    public List<RoleEvaluationCase> generateDraftCases(RoleEvaluation evaluation,
                                                        RoleCharacter character,
                                                        String datasetVersion,
                                                        int limit) {
        String version = requireText(datasetVersion, "datasetVersion不能为空");
        List<RoleEvaluationCase> cases = new ArrayList<>();
        for (RoleEvaluationPassageSelectionService.SelectedPassage selected
                : passageSelectionService.select(evaluation, character, limit)) {
            NovelPassage passage = selected.passage();
            RoleEvaluationCaseDraftResult draft = buildCaseDraft(character, passage);
            if (draft == null || isBlank(draft.testInput())) {
                continue;
            }
            cases.add(insertDraftCase(evaluation, character, passage, selected.examples(), version, draft));
        }
        return cases;
    }

    /**
     * 审核评测案例，使其可参与真实评测运行。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @return 已审核案例
     */
    public RoleEvaluationCase approveCase(Long evaluationId, Long caseId) {
        RoleEvaluationCase evaluationCase = requireEvaluationCase(evaluationId, caseId);
        evaluationCase.setStatus(APPROVED);
        evaluationCase.setReviewedTime(LocalDateTime.now());
        caseMapper.updateById(evaluationCase);
        return evaluationCase;
    }

    /**
     * 拒绝不适合作为评测基线的案例草稿。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @return 已拒绝案例
     */
    public RoleEvaluationCase rejectCase(Long evaluationId, Long caseId) {
        RoleEvaluationCase evaluationCase = requireEvaluationCase(evaluationId, caseId);
        evaluationCase.setStatus("REJECTED");
        evaluationCase.setReviewedTime(LocalDateTime.now());
        caseMapper.updateById(evaluationCase);
        return evaluationCase;
    }

    /**
     * 查询指定独立评测、数据集版本下的案例。
     *
     * @param evaluationId 独立评测主键
     * @param datasetVersion 数据集版本；为空时查询全部版本
     * @return 按主键倒序排列的案例列表
     */
    public List<RoleEvaluationCase> listCases(Long evaluationId, String datasetVersion) {
        return caseMapper.selectList(new LambdaQueryWrapper<RoleEvaluationCase>()
                .eq(RoleEvaluationCase::getEvaluationId, evaluationId)
                .eq(!isBlank(datasetVersion), RoleEvaluationCase::getDatasetVersion, datasetVersion)
                .orderByDesc(RoleEvaluationCase::getId));
    }

    /**
     * 查询并校验案例属于指定独立评测。
     *
     * @param evaluationId 独立评测主键
     * @param caseId 评测案例主键
     * @return 归属正确的案例
     */
    public RoleEvaluationCase requireEvaluationCase(Long evaluationId, Long caseId) {
        RoleEvaluationCase evaluationCase = caseMapper.selectById(caseId);
        if (evaluationCase == null) {
            throw new IllegalArgumentException("评测案例不存在: " + caseId);
        }
        if (!Objects.equals(evaluationId, evaluationCase.getEvaluationId())) {
            throw new IllegalStateException("评测案例不属于当前角色评测");
        }
        return evaluationCase;
    }

    /**
     * 调用模型，以原作 Passage 构造角色反应测试输入和评分标准。
     *
     * @param character 目标角色
     * @param passage 来源原文块
     * @return 案例草稿；模型异常由调用方继续抛出
     */
    private RoleEvaluationCaseDraftResult buildCaseDraft(RoleCharacter character, NovelPassage passage) {
        PromptTemplateRenderer.DualPrompt prompt = renderer.renderDual(
                "prompts/system/role-evaluation-case-build.st",
                "prompts/user/role-evaluation-case-build.st",
                Map.of("characterName", character.getCharacterName(), "passage", passage.getContent()));
        return chatClient.prompt().system(prompt.systemPrompt()).user(prompt.userPrompt())
                .call().entity(RoleEvaluationCaseDraftResult.class);
    }

    /**
     * 持久化一条包含公共角色资产快照的案例草稿。
     *
     * @param evaluation 目标独立评测
     * @param character 公共角色
     * @param passage 来源原文块
     * @param sourceExamples 同一 Passage 下的角色样本
     * @param datasetVersion 数据集版本
     * @param draft 模型生成的草稿内容
     * @return 已保存的案例
     */
    private RoleEvaluationCase insertDraftCase(RoleEvaluation evaluation,
                                                RoleCharacter character,
                                                NovelPassage passage,
                                                List<RoleExample> sourceExamples,
                                                String datasetVersion,
                                                RoleEvaluationCaseDraftResult draft) {
        RoleEvaluationCase evaluationCase = new RoleEvaluationCase();
        evaluationCase.setEvaluationId(evaluation.getId());
        evaluationCase.setDatasetVersion(datasetVersion);
        // 角色和 Passage 快照用于审计，防止公共资产重建后无法解释历史评分。
        evaluationCase.setCharacterId(character.getId());
        evaluationCase.setPassageId(passage.getId());
        evaluationCase.setSourceExampleIds(toJson(sourceExamples.stream().map(RoleExample::getId).toList()));
        evaluationCase.setTestInput(draft.testInput().trim());
        evaluationCase.setSourcePassage(passage.getContent());
        evaluationCase.setExpectedBehaviors(trimToNull(draft.expectedBehaviors()));
        evaluationCase.setScoringRubric(trimToNull(draft.scoringRubric()));
        evaluationCase.setDifficulty(normalizeDifficulty(draft.difficulty()));
        evaluationCase.setStatus(DRAFT);
        caseMapper.insert(evaluationCase);
        return evaluationCase;
    }

    /**
     * 将对象序列化为评测审计字段使用的 JSON 文本。
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
     * 规范化难度标签。
     *
     * @param difficulty 模型返回的难度
     * @return 合法难度标签
     */
    private String normalizeDifficulty(String difficulty) {
        if ("EASY".equalsIgnoreCase(difficulty) || "HARD".equalsIgnoreCase(difficulty)) {
            return difficulty.toUpperCase();
        }
        return "MEDIUM";
    }

    /**
     * 校验文本非空并返回去除首尾空白后的值。
     *
     * @param value 文本值
     * @param message 参数非法时的提示
     * @return 规范化文本
     */
    private String requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
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

    /**
     * 将空白文本规整为 null。
     *
     * @param value 文本值
     * @return 规整结果
     */
    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
