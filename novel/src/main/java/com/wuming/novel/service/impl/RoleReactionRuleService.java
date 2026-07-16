package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.novel.domain.dto.ReactionSituationGroup;
import com.wuming.novel.domain.dto.RoleReactionRuleBuildResult;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.infrastructure.mapper.RoleReactionRuleMapper;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.integration.rpc.rag.RoleExampleVectorIndexService;
import com.wuming.novel.integration.rpc.rag.RoleReactionRuleVectorIndexService;
import com.wuming.novel.llm.LlmConcurrencyLimiter;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.IRoleReactionRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 角色情境反应规则基础服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleReactionRuleService
        extends ServiceImpl<RoleReactionRuleMapper, RoleReactionRule>
        implements IRoleReactionRuleService {
    private static final String RULE_BUILD_SYSTEM_TEMPLATE = "prompts/system/role-reaction-rule-build.st";
    private static final String RULE_BUILD_USER_TEMPLATE = "prompts/user/role-reaction-rule-build.st";
    private static final String VECTOR_PENDING = "PENDING";
    private static final String INCOMPLETE = "INCOMPLETE";
    private static final String EVIDENCE_NOT_ENOUGH = "证据不足";

    private final IRoleCharacterService roleCharacterService;
    private final RoleExampleVectorIndexService roleExampleVectorIndexService;
    private final ChatClient chatClient;
    private final PromptTemplateRenderer renderer;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;
    private final RoleReactionRuleVectorIndexService roleReactionRuleVectorIndexService;
    private final TransactionTemplate transactionTemplate;

    @Value("${novel.reaction-rule.situations-config:classpath:reaction-situations.json}")
    private String situationsConfig;

    @Value("${novel.reaction-rule.example-top-k:10}")
    private int exampleTopK;

    @Value("${novel.reaction-rule.example-top-n:5}")
    private int exampleTopN;

    @Value("${novel.reaction-rule.min-confidence:0.6}")
    private double minConfidence;

    /**
     * 查询预定义情境任务标识，用于Pipeline按情境记录检查点。
     *
     * @param characterId 角色id
     * @return 情境任务标识列表
     */
    @Override
    public List<String> situationKeys(Long characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            throw new IllegalArgumentException("角色不存在: " + characterId);
        }
        return situationTasks().stream()
                .map(this::situationKey)
                .toList();
    }

    /**
     * 构建并重建单个情境上的角色反应规则。
     *
     * <p>证据不足时会清理该情境旧规则并返回0，调用方可将该情境视为已处理。</p>
     *
     * @param characterId 角色id
     * @param situationKey 情境任务标识
     * @return 本次保存的规则数量
     */
    @Override
    public int buildRule(Long characterId, String situationKey) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        if (situationKey == null || situationKey.isBlank()) {
            throw new IllegalArgumentException("situationKey不能为空");
        }
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            throw new IllegalArgumentException("角色不存在: " + characterId);
        }
        SituationTask task = requireSituationTask(situationKey);
        RoleReactionRule rule = llmConcurrencyLimiter.execute(() -> buildOneRule(character, task));
        PersistedRules persisted = Objects.requireNonNull(transactionTemplate.execute(status -> {
            List<Long> oldRuleIds = list(new LambdaQueryWrapper<RoleReactionRule>()
                    .select(RoleReactionRule::getId)
                    .eq(RoleReactionRule::getCharacterId, character.getId())
                    .eq(RoleReactionRule::getSituation, task.situation()))
                    .stream()
                    .map(RoleReactionRule::getId)
                    .toList();
            if (!oldRuleIds.isEmpty()) {
                remove(new LambdaQueryWrapper<RoleReactionRule>()
                        .eq(RoleReactionRule::getCharacterId, character.getId())
                        .eq(RoleReactionRule::getSituation, task.situation()));
            }
            if (rule == null) {
                return new PersistedRules(oldRuleIds, List.of(), 0);
            }
            save(rule);
            List<Long> newRuleIds = rule.getId() == null ? List.of() : List.of(rule.getId());
            return new PersistedRules(oldRuleIds, newRuleIds, 1);
        }));

        // 数据库事务完成并释放连接后，再执行可能耗时的远程索引操作。
        syncRoleReactionRuleIndex(character, persisted.oldRuleIds(), persisted.newRuleIds(), persisted.ruleCount());
        return persisted.ruleCount();
    }

    /**
     * 完成反应规则构建阶段后的角色状态标记。
     *
     * <p>阶段重试时本轮可能没有新增规则，因此以数据库中的角色规则总量判断是否已有有效结果。</p>
     *
     * @param characterId 角色id
     */
    @Override
    public void completeRuleBuild(Long characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            throw new IllegalArgumentException("角色不存在: " + characterId);
        }
        long ruleCount = count(new LambdaQueryWrapper<RoleReactionRule>()
                .eq(RoleReactionRule::getCharacterId, characterId));
        if (ruleCount <= 0) {
            markIncomplete(character, "未生成有效ReactionRule");
            return;
        }
        markIncomplete(character, "ReactionRule已构建，待构建Profile");
    }


    /**
     * 为单个情境构建角色反应规则。
     *
     * @param character 角色
     * @param task 情境任务
     * @return 构建成功的规则；证据不足或构建失败时返回null
     */
    private RoleReactionRule buildOneRule(RoleCharacter character, SituationTask task) {
        List<SearchHit> examples = retrieveExamples(character.getId(), retrievalQuery(character, task));
        if (examples.isEmpty()) {
            log.debug("情境未召回角色样本，characterId: {}, category: {}, situationKey: {}",
                    character.getId(), task.category(), situationKey(task));
            return null;
        }

        RoleReactionRuleBuildResult result = buildRuleByLlm(character, task, examples);
        if (result == null || isEvidenceNotEnough(result)) {
            log.debug("情境证据不足，characterId: {}, category: {}, situationKey: {}",
                    character.getId(), task.category(), situationKey(task));
            return null;
        }

        RoleReactionRule rule = new RoleReactionRule();
        rule.setCharacterId(character.getId());
        rule.setCharacterName(character.getCharacterName());
        rule.setSituation(task.situation());
        rule.setRule(result.rule().trim());
        rule.setEvidencePassageIds(evidencePassageIds(examples));
        rule.setVectorStatus(VECTOR_PENDING);
        log.debug("情境反应规则构建成功，characterId: {}, category: {}, situationKey: {}",
                character.getId(), task.category(), situationKey(task));
        return rule;
    }

    /**
     * 召回情境相关角色样本。查询改写、多路召回和重排序由RAG模块统一处理。
     *
     * @param characterId 角色id
     * @param query 情境检索query
     * @return 召回到的角色样本
     */
    private List<SearchHit> retrieveExamples(Long characterId, String query) {
        if (query == null || query.isBlank()) {
            log.warn("情境反应规则召回跳过，query为空，characterId: {}", characterId);
            return List.of();
        }
        return roleExampleVectorIndexService.search(
                        characterId,
                        query.trim(),
                        true,
                        exampleTopK,
                        exampleTopN
                ).stream()
                .filter(hit -> hit != null
                        && hit.getDocumentId() != null
                        && hit.getContent() != null
                        && !hit.getContent().isBlank())
                .toList();
    }

    /**
     * 构造情境样本召回查询。
     *
     * <p>优先保留情境名称，再补充配置中的触发模式。这样多路改写前的基础query稳定且非空，
     * 同时能让召回覆盖更多同义场景。</p>
     */
    private String retrievalQuery(RoleCharacter character, SituationTask task) {
        List<String> parts = new java.util.ArrayList<>();
        if (task.situation() != null && !task.situation().isBlank()) {
            parts.add(task.situation().trim());
        }
        if (task.scenePatterns() != null) {
            for (String pattern : task.scenePatterns()) {
                if (pattern != null && !pattern.isBlank()) {
                    parts.add(pattern.replace("{characterName}", character.getCharacterName()).trim());
                }
            }
        }
        return String.join("；", parts);
    }

    /**
     * 提示词构建，调用llm构建rule
     * @param character 传递角色信息
     * @param task 情景
     * @param examples 召回文档
     * @return RoleReactionRuleBuildResult
     */
    private RoleReactionRuleBuildResult buildRuleByLlm(RoleCharacter character,
                                                       SituationTask task,
                                                       List<SearchHit> examples) {
        PromptTemplateRenderer.DualPrompt dualPrompt = renderer.renderDual(
                RULE_BUILD_SYSTEM_TEMPLATE,
                RULE_BUILD_USER_TEMPLATE,
                Map.of(
                        "characterName", character.getCharacterName(),
                        "category", task.category(),
                        "situation", task.situation(),
                        "examples", formatExamples(examples)
                )
        );
        return chatClient.prompt()
                .system(dualPrompt.systemPrompt())
                .user(dualPrompt.userPrompt())
                .call()
                .entity(RoleReactionRuleBuildResult.class);
    }

    /**
     * 事务内持久化结果，用于事务外同步向量索引。
     */
    private record PersistedRules(List<Long> oldRuleIds, List<Long> newRuleIds, int ruleCount) {
    }

    /**
     * 删除旧反应规则向量并写入新规则向量。
     *
     * @param character 角色
     * @param oldRuleIds 待删除向量的旧规则id
     * @param newRuleIds 待写入向量的新规则id
     * @param ruleCount 本次保存的规则数量
     */
    private void syncRoleReactionRuleIndex(RoleCharacter character,
                                           List<Long> oldRuleIds,
                                           List<Long> newRuleIds,
                                           int ruleCount) {
        if (newRuleIds.isEmpty() && ruleCount > 0) {
            throw new IllegalStateException("角色反应规则保存后未获取到规则主键，无法同步索引，characterId: "
                    + character.getId());
        }
        int deletedCount = roleReactionRuleVectorIndexService.deleteByIds(character.getId(), oldRuleIds);
        requireRagSuccess("删除旧ReactionRule向量", deletedCount);
        int indexedCount = roleReactionRuleVectorIndexService.indexByIds(newRuleIds);
        requireRagSuccess("索引ReactionRule向量", indexedCount);
        log.debug("角色反应规则向量同步索引完成，novelId: {}, characterId: {}, characterName: {}, deletedCount: {}, indexedCount: {}",
                character.getNovelId(), character.getId(), character.getCharacterName(), deletedCount, indexedCount);
    }

    /**
     * 检查RAG调用结果，负数代表远程服务降级。
     *
     * @param action 当前动作
     * @param result RAG调用返回值
     */
    private void requireRagSuccess(String action, int result) {
        if (result < 0) {
            throw new IllegalStateException(action + "失败：RAG服务降级");
        }
    }

    /**
     * 判断llm响应结果是否满足
     *
     * @param result LLM构建规则结果
     * @return true表示证据不足或置信度不达标
     */
    private boolean isEvidenceNotEnough(RoleReactionRuleBuildResult result) {
        String rule = result.rule();
        double confidence = result.confidence() == null ? 0.0 : result.confidence();
        return rule == null
                || rule.isBlank()
                || EVIDENCE_NOT_ENOUGH.equals(rule.trim())
                || confidence < minConfidence;
    }

    /**
     * 将召回内容拼接起来
     * @param examples 召回内容
     * @return 拼接完成的字符串
     */
    private String formatExamples(List<SearchHit> examples) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < examples.size(); i++) {
            SearchHit hit = examples.get(i);
            builder.append("样本").append(i + 1)
                    .append("，passageId=").append(metadataLong(hit, "passage_id"))
                    .append("：\n")
                    .append(hit.getContent())
                    .append("\n\n");
        }
        return builder.toString();
    }

    /**
     * 从召回样本元数据中提取证据Passage id。
     *
     * @param examples 召回样本
     * @return 去重后的证据Passage id列表
     */
    private List<Long> evidencePassageIds(List<SearchHit> examples) {
        return examples.stream()
                .map(hit -> metadataLong(hit, "passage_id"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 将召回文档元数据中的某个字段转为Long
     *
     * @param hit 召回文档
     * @param key 字段
     * @return Long值
     */
    private Long metadataLong(SearchHit hit, String key) {
        if (hit.getMetadata() == null) {
            return null;
        }
        Object value = hit.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 将角色构建状态标记为incomplete，尚未完成
     *
     * @param character 角色
     * @param reason 未完成原因
     */
    private void markIncomplete(RoleCharacter character, String reason) {
        character.setBuildStatus(INCOMPLETE);
        character.setBuildError(reason);
        roleCharacterService.updateById(character);
    }

    /**
     * 从预定义json文件中读取描述
     *
     * @return 不同分类的场景
     */
    private List<ReactionSituationGroup> loadSituationGroups() {
        org.springframework.core.io.Resource resource = resourceLoader.getResource(situationsConfig);
        try (var inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("读取反应规则情境配置失败: " + situationsConfig, e);
        }
    }

    /**
     * 将预定义情境配置展开为构建任务。
     *
     * @return 情境任务列表
     */
    private List<SituationTask> situationTasks() {
        return loadSituationGroups().stream()
                .flatMap(group -> group.situations().stream()
                        .filter(s -> s != null && s.situation() != null && !s.situation().isBlank())
                        .map(s -> new SituationTask(group.category(), s.situation().trim(), s.scenePatterns())))
                .toList();
    }

    /**
     * 按情境任务标识查找情境任务。
     *
     * @param situationKey 情境任务标识
     * @return 情境任务
     */
    private SituationTask requireSituationTask(String situationKey) {
        return situationTasks().stream()
                .filter(task -> Objects.equals(situationKey(task), situationKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("情境任务不存在: " + situationKey));
    }

    /**
     * 构建稳定的情境任务标识。
     *
     * @param task 情境任务
     * @return 情境任务标识
     */
    private String situationKey(SituationTask task) {
        return task.category() + "::" + task.situation();
    }

    private record SituationTask(String category, String situation, List<String> scenePatterns) {
    }
}
