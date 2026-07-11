package com.wuming.novel.service.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.novel.domain.dto.RoleEvaluationJudgeResult;
import com.wuming.novel.domain.dto.RoleEvaluationImprovementBatchResult;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleEvaluation;
import com.wuming.novel.domain.entity.RoleEvaluationCase;
import com.wuming.novel.domain.entity.RoleEvaluationRun;
import com.wuming.novel.domain.entity.RoleProfile;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.infrastructure.mapper.RoleCharacterMapper;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.integration.rpc.rag.RoleExampleVectorIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 执行角色评测中的模型调用，包括样本召回、角色回复生成、Judge 评分和规则建议生成。
 */
@Service
@RequiredArgsConstructor
public class RoleEvaluationRunner {
    private final RoleCharacterMapper characterMapper;
    private final RoleExampleVectorIndexService exampleService;
    private final UserRoleProfileService profileService;
    private final UserRoleReactionRuleService ruleService;
    private final PromptTemplateRenderer renderer;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("${novel.role-evaluation.role-example-top-k:10}")
    private int topK;

    @Value("${novel.role-evaluation.role-example-top-n:3}")
    private int topN;

    /**
     * 运行一次完整评测，并将召回、回复、评分或遮蔽失败信息写入传入的运行记录。
     * 调用方负责在本方法正常返回或抛出异常后持久化运行记录。
     *
     * @param evaluation 独立评测及其当前个人角色版本
     * @param evaluationCase 已审核的评测案例
     * @param run 当前运行记录
     */
    public void run(RoleEvaluation evaluation, RoleEvaluationCase evaluationCase, RoleEvaluationRun run) {
        RoleCharacter character = requireCompletedCharacter(evaluationCase.getCharacterId());
        RoleProfile profile = profileService.loadEffectiveProfile(character.getId(), evaluation.getUserRoleVersionId());
        List<SearchHit> examples = exampleService.search(
                character.getId(),
                evaluationCase.getTestInput(),
                null,
                Math.max(1, topK),
                true,
                Math.max(1, topN),
                evaluationCase.getPassageId());
        run.setRetrievedDocuments(toJson(examples));

        // RAG 侧已按 Passage 过滤；这里复核实际命中，防止过滤条件失效时原文泄漏进入评分。
        if (containsExcludedPassage(examples, evaluationCase.getPassageId())) {
            run.setStatus("INVALID");
            run.setFailureReason("角色样本召回包含被遮蔽的Passage");
            return;
        }

        List<RoleReactionRule> rules = ruleService.loadEffectiveRules(
                evaluation.getCharacterId(), evaluation.getUserRoleVersionId());
        long startTime = System.currentTimeMillis();
        String response = chatClient.prompt()
                .system(buildResponseSystemPrompt(character, profile, examples, rules))
                .user(evaluationCase.getTestInput())
                .call()
                .content();
        run.setResponseContent(response);
        run.setGenerationCostMs(System.currentTimeMillis() - startTime);

        RoleEvaluationJudgeResult judge = judge(evaluationCase, response);
        validateJudge(judge);
        run.setJudgeResult(toJson(judge));
        run.setTotalScore(judge.totalScore());
        run.setJudgeReason(judge.reason());
        run.setStatus("SUCCEEDED");
    }

    /**
     * 对同一角色版本的多条低分运行进行一次性汇总，输出有限且有重复证据支持的规则建议。
     *
     * @param evidenceRuns 已校验归属和版本一致性的低分运行证据
     * @param rules 当前有效角色规则
     * @param maxChanges 本次汇总最多允许提出的规则修改数量
     * @return 结构化的批次汇总与规则建议
     */
    public RoleEvaluationImprovementBatchResult buildImprovementBatch(List<ImprovementRunEvidence> evidenceRuns,
                                                                       List<RoleReactionRule> rules,
                                                                       int maxChanges) {
        String ruleText = rules.stream()
                .map(rule -> "规则ID：" + rule.getId() + "；情境：" + rule.getSituation() + "；规则：" + rule.getRule())
                .collect(Collectors.joining("\n"));
        String evidenceText = evidenceRuns.stream().map(this::formatEvidence).collect(Collectors.joining("\n\n"));
        PromptTemplateRenderer.DualPrompt prompt = renderer.renderDual(
                "prompts/system/role-evaluation-rule-improvement-batch.st",
                "prompts/user/role-evaluation-rule-improvement-batch.st",
                Map.of("rules", ruleText, "runEvidence", evidenceText, "maxChanges", maxChanges));
        return chatClient.prompt().system(prompt.systemPrompt()).user(prompt.userPrompt())
                .call().entity(RoleEvaluationImprovementBatchResult.class);
    }

    /**
     * 调用 Judge 模型，从角色一致性、情境回应等维度评估生成回复。
     *
     * @param evaluationCase 当前评测案例
     * @param responseContent 角色生成回复
     * @return 结构化评分结果
     */
    private RoleEvaluationJudgeResult judge(RoleEvaluationCase evaluationCase, String responseContent) {
        PromptTemplateRenderer.DualPrompt prompt = renderer.renderDual(
                "prompts/system/role-evaluation-judge.st",
                "prompts/user/role-evaluation-judge.st",
                Map.of(
                        "testInput", evaluationCase.getTestInput(),
                        "sourcePassage", evaluationCase.getSourcePassage(),
                        "expectedBehaviors", nullToEmpty(evaluationCase.getExpectedBehaviors()),
                        "scoringRubric", nullToEmpty(evaluationCase.getScoringRubric()),
                        "responseContent", nullToEmpty(responseContent)
                ));
        return chatClient.prompt().system(prompt.systemPrompt()).user(prompt.userPrompt())
                .call().entity(RoleEvaluationJudgeResult.class);
    }

    /**
     * 将一条运行及其案例整理为可供汇总模型读取的文本证据。
     *
     * @param evidence 运行与案例证据
     * @return 格式化后的提示词片段
     */
    private String formatEvidence(ImprovementRunEvidence evidence) {
        RoleEvaluationRun run = evidence.run();
        RoleEvaluationCase evaluationCase = evidence.evaluationCase();
        return "运行ID：%s\n测试输入：%s\n原作Passage：%s\n角色回复：%s\n总分：%s\nJudge反馈：%s"
                .formatted(run.getId(), evaluationCase.getTestInput(), evaluationCase.getSourcePassage(),
                        nullToEmpty(run.getResponseContent()), run.getTotalScore(), nullToEmpty(run.getJudgeReason()));
    }

    /**
     * 基于有效角色资产组装评测专用的角色回复系统提示词。
     *
     * @param character 目标角色
     * @param profile 有效画像快照
     * @param examples 未命中遮蔽 Passage 的互动样本
     * @param rules 有效情境反应规则
     * @return 角色回复系统提示词
     */
    private String buildResponseSystemPrompt(RoleCharacter character,
                                             RoleProfile profile,
                                             List<SearchHit> examples,
                                             List<RoleReactionRule> rules) {
        StringBuilder builder = new StringBuilder();
        builder.append("你正在扮演小说《").append(character.getNovelName()).append("》中的角色「")
                .append(character.getCharacterName()).append("》。\n")
                .append("核心性格：").append(nullToEmpty(profile.getCoreTraits())).append("\n")
                .append("行为禁忌：").append(nullToEmpty(profile.getForbiddenBehaviors())).append("\n")
                .append("请自然、简洁地回复用户，不要提及画像、规则、检索或评测。\n");
        if (profile.getSpeakingStyle() != null) {
            builder.append("说话风格：").append(nullToEmpty(profile.getSpeakingStyle().getSignature())).append("\n")
                    .append("典型表达：").append(joinValues(profile.getSpeakingStyle().getDistinctivePatterns())).append("\n")
                    .append("避免表达：").append(joinValues(profile.getSpeakingStyle().getAvoidPatterns())).append("\n");
        }
        if (!rules.isEmpty()) {
            builder.append("【情境反应规则】\n");
            rules.forEach(rule -> builder.append("- ").append(rule.getSituation()).append("：")
                    .append(rule.getRule()).append("\n"));
        }
        if (!examples.isEmpty()) {
            builder.append("【原作互动样本】\n");
            examples.forEach(hit -> builder.append("- ").append(hit.getContent()).append("\n"));
        }
        return builder.toString();
    }

    /**
     * 判断样本命中是否包含当前案例应遮蔽的来源 Passage。
     *
     * @param hits 实际角色样本命中
     * @param excludedPassageId 应排除的 Passage 主键
     * @return 命中中存在被遮蔽 Passage 时返回 true
     */
    private boolean containsExcludedPassage(List<SearchHit> hits, Long excludedPassageId) {
        return hits.stream()
                .anyMatch(hit -> Objects.equals(toLong(hit.getMetadata().get("passage_id")), excludedPassageId));
    }

    /**
     * 校验 Judge 返回的评分与理由是否完整且处于允许范围。
     *
     * @param judge Judge 结果
     */
    private void validateJudge(RoleEvaluationJudgeResult judge) {
        if (judge == null || judge.totalScore() == null || nullToEmpty(judge.reason()).isBlank()
                || !validScore(judge.characterConsistency()) || !validScore(judge.situationResponse())
                || !validScore(judge.sourceFaithfulness()) || !validScore(judge.styleNaturalness())
                || !validScore(judge.boundaryCompliance())) {
            throw new IllegalStateException("Judge返回结果不完整或评分超出范围");
        }
    }

    /**
     * 查询并校验角色已完成构建。
     *
     * @param characterId 角色主键
     * @return 可参与评测的角色
     */
    private RoleCharacter requireCompletedCharacter(Long characterId) {
        RoleCharacter character = characterMapper.selectById(characterId);
        if (character == null) {
            throw new IllegalArgumentException("角色不存在: " + characterId);
        }
        if (!"COMPLETED".equals(character.getBuildStatus())) {
            throw new IllegalStateException("角色构建尚未完成");
        }
        return character;
    }

    /**
     * 将审计对象序列化为 JSON 文本。
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
     * 将 RAG 元数据中的数值转换为 Long。
     *
     * @param value 元数据值
     * @return 转换后的数值，无法转换时返回 null
     */
    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 判断评分是否处于 1 到 5 的闭区间。
     *
     * @param score 评分值
     * @return 合法时返回 true
     */
    private boolean validScore(Integer score) {
        return score != null && score >= 1 && score <= 5;
    }

    /**
     * 将 null 转换为提示词模板可用的空字符串。
     *
     * @param value 文本值
     * @return 非 null 文本
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 将可选文本列表拼接为中文分隔的提示词内容。
     *
     * @param values 文本列表
     * @return 拼接结果；列表为空时返回空字符串
     */
    private String joinValues(List<String> values) {
        return values == null ? "" : values.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("；"));
    }

    /**
     * 一条可用于批量改进分析的运行与案例组合。
     *
     * @param run 已成功完成的低分运行
     * @param evaluationCase 该运行所属评测案例
     */
    public record ImprovementRunEvidence(RoleEvaluationRun run, RoleEvaluationCase evaluationCase) {
    }
}
