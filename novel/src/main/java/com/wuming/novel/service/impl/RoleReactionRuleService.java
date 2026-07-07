package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.novel.domain.dto.ReactionSituationGroup;
import com.wuming.novel.domain.dto.RoleReactionQueryRewriteResult;
import com.wuming.novel.domain.dto.RoleReactionRuleBuildResult;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.infrastructure.mapper.RoleReactionRuleMapper;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.integration.message.EventPublisher;
import com.wuming.novel.integration.message.rolereactionruleindex.RoleReactionRuleIndexEvent;
import com.wuming.novel.integration.rpc.rag.RoleExampleVectorIndexService;
import com.wuming.novel.llm.LlmConcurrencyLimiter;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.IRoleReactionRuleService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 角色情境反应规则基础服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleReactionRuleService
        extends ServiceImpl<RoleReactionRuleMapper, RoleReactionRule>
        implements IRoleReactionRuleService {
    private static final String QUERY_REWRITE_TEMPLATE = "prompts/role-reaction-query-rewrite.st";
    private static final String RULE_BUILD_TEMPLATE = "prompts/role-reaction-rule-build.st";
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
    private final EventPublisher<RoleReactionRuleIndexEvent> roleReactionRuleIndexEventPublisher;
    @Lazy
    @Autowired
    private RoleReactionRuleService self;
    @Resource(name = "llmExecutor")
    private Executor llmExecutor;

    @Value("${novel.reaction-rule.situations-config:classpath:reaction-situations.json}")
    private String situationsConfig;

    @Value("${novel.reaction-rule.example-top-k:10}")
    private int exampleTopK;

    @Value("${novel.reaction-rule.example-top-n:5}")
    private int exampleTopN;

    @Value("${novel.reaction-rule.rerank:true}")
    private boolean rerank;

    @Value("${novel.reaction-rule.min-confidence:0.6}")
    private double minConfidence;

    @Override
    public int buildRules(Long characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            throw new IllegalArgumentException("角色不存在: " + characterId);
        }

        List<SituationTask> tasks = loadSituationGroups().stream()
                .flatMap(group -> group.situations().stream()
                        .filter(situation -> situation != null && !situation.isBlank())
                        .map(situation -> new SituationTask(group.category(), situation.trim())))
                .toList();
        if (tasks.isEmpty()) {
            log.warn("未读取到情境反应规则配置，characterId: {}, config: {}", characterId, situationsConfig);
            return 0;
        }

        log.info("开始构建角色情境反应规则，characterId: {}, characterName: {}, situationCount: {}",
                characterId, character.getCharacterName(), tasks.size());
        List<CompletableFuture<RoleReactionRule>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                        () -> llmConcurrencyLimiter.execute(() -> buildOneRule(character, task)),
                        llmExecutor))
                .toList();
        List<RoleReactionRule> rules = futures.stream()
                .map(future -> future.exceptionally(e -> {
                    log.warn("角色情境反应规则构建任务失败，characterId: {}", characterId, e);
                    return null;
                }).join())
                .filter(Objects::nonNull)
                .toList();
        if (rules.isEmpty()) {
            markIncomplete(character, "未生成有效ReactionRule");
            log.info("角色情境反应规则构建完成，characterId: {}, savedCount: 0", characterId);
            return 0;
        }

        int savedCount = self.persistBuiltRules(characterId, rules);
        log.info("角色情境反应规则构建完成，characterId: {}, savedCount: {}", characterId, savedCount);
        return savedCount;
    }

    /**
     * 持久化已构建的情境反应规则。
     *
     * @param characterId 角色id
     * @param rules 已构建规则
     * @return 本次保存的规则数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int persistBuiltRules(Long characterId, List<RoleReactionRule> rules) {
        List<Long> oldRuleIds = list(new LambdaQueryWrapper<RoleReactionRule>()
                .select(RoleReactionRule::getId)
                .eq(RoleReactionRule::getCharacterId, characterId))
                .stream()
                .map(RoleReactionRule::getId)
                .toList();
        remove(new LambdaQueryWrapper<RoleReactionRule>()
                .eq(RoleReactionRule::getCharacterId, characterId));
        saveBatch(rules);
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character != null) {
            publishRoleReactionRuleIndexEvent(character, oldRuleIds, rules);
            markIncomplete(character, "ReactionRule已构建，待构建Profile");
        }
        return rules.size();
    }

    private void publishRoleReactionRuleIndexEvent(RoleCharacter character,
                                                   List<Long> oldRuleIds,
                                                   List<RoleReactionRule> rules) {
        List<Long> newRuleIds = rules.stream()
                .map(RoleReactionRule::getId)
                .filter(Objects::nonNull)
                .toList();
        if (newRuleIds.isEmpty()) {
            log.warn("角色反应规则保存后未获取到规则主键，跳过向量索引，characterId: {}, ruleCount: {}",
                    character.getId(), rules.size());
            return;
        }

        RoleReactionRuleIndexEvent event = new RoleReactionRuleIndexEvent();
        event.setNovelId(character.getNovelId());
        event.setCharacterId(character.getId());
        event.setCharacterName(character.getCharacterName());
        event.setDeletedRuleIds(oldRuleIds);
        event.setIndexedRuleIds(newRuleIds);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    roleReactionRuleIndexEventPublisher.publish(event);
                }
            });
            return;
        }
        roleReactionRuleIndexEventPublisher.publish(event);
    }

    private RoleReactionRule buildOneRule(RoleCharacter character, SituationTask task) {
        try {
            String query = rewriteQuery(character, task);
            // 召回文档
            List<SearchHit> examples = retrieveExamples(character.getId(), query);
            if (examples.isEmpty()) {
                log.debug("情境未召回角色样本，characterId: {}, category: {}, situation: {}",
                        character.getId(), task.category(), task.situation());
                return null;
            }

            // 调用llm构建规则
            RoleReactionRuleBuildResult result = buildRuleByLlm(character, task, examples);
            if (result == null || isEvidenceNotEnough(result)) {
                log.debug("情境证据不足，characterId: {}, category: {}, situation: {}",
                        character.getId(), task.category(), task.situation());
                return null;
            }

            // 创建实体
            RoleReactionRule rule = new RoleReactionRule();
            rule.setCharacterId(character.getId());
            rule.setCharacterName(character.getCharacterName());
            rule.setSituation(task.situation());
            rule.setRule(result.rule().trim());
            rule.setEvidencePassageIds(evidencePassageIds(examples));
            rule.setVectorStatus(VECTOR_PENDING);
            log.debug("情境反应规则构建成功，characterId: {}, category: {}, situation: {}",
                    character.getId(), task.category(), task.situation());
            return rule;
        } catch (RuntimeException e) {
            log.warn("情境反应规则构建失败，characterId: {}, category: {}, situation: {}",
                    character.getId(), task.category(), task.situation(), e);
            return null;
        }
    }

    /**
     * 重写情境语句
     */
    private String rewriteQuery(RoleCharacter character, SituationTask task) {
        String prompt = renderer.render(QUERY_REWRITE_TEMPLATE, Map.of(
                "characterName", character.getCharacterName(),
                "category", task.category(),
                "situation", task.situation()
        ));
        RoleReactionQueryRewriteResult result = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(RoleReactionQueryRewriteResult.class);
        if (result == null || result.query() == null || result.query().isBlank()) {
            return task.situation();
        }
        return result.query().trim();
    }

    /**
     * 召回角色样本
     * @param characterId 角色id
     * @param query 用户查询
     * @return 召回文档
     */
    private List<SearchHit> retrieveExamples(Long characterId, String query) {
        Map<String, SearchHit> hits = new LinkedHashMap<>();
        List<SearchHit> queryHits = roleExampleVectorIndexService.search(
                characterId,
                query,
                exampleTopK,
                rerank,
                exampleTopN
        );
        for (SearchHit hit : queryHits) {
            if (hit == null || hit.getDocumentId() == null || hit.getContent() == null || hit.getContent().isBlank()) {
                continue;
            }
            hits.putIfAbsent(hit.getDocumentId(), hit);
        }
        return new ArrayList<>(hits.values());
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
        String prompt = renderer.render(RULE_BUILD_TEMPLATE, Map.of(
                "characterName", character.getCharacterName(),
                "category", task.category(),
                "situation", task.situation(),
                "examples", formatExamples(examples)
        ));
        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(RoleReactionRuleBuildResult.class);
    }

    /**
     * 判断llm响应结果是否满足
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

    private List<Long> evidencePassageIds(List<SearchHit> examples) {
        return examples.stream()
                .map(hit -> metadataLong(hit, "passage_id"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 将召回文档元数据中的某个字段转为Long
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
     */
    private void markIncomplete(RoleCharacter character, String reason) {
        character.setBuildStatus(INCOMPLETE);
        character.setBuildError(reason);
        roleCharacterService.updateById(character);
    }

    /**
     * 从预定义json文件中读取描述
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

    private record SituationTask(String category, String situation) {
    }
}
