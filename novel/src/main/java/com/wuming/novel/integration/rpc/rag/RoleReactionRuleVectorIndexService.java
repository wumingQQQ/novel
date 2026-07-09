package com.wuming.novel.integration.rpc.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.infrastructure.mapper.RoleReactionRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色反应规则向量索引服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleReactionRuleVectorIndexService {
    private static final String VECTOR_PENDING = "PENDING";
    private static final String VECTOR_INDEXED = "INDEXED";
    private static final String VECTOR_FAILED = "FAILED";

    private final RagService ragService;
    private final RoleReactionRuleMapper roleReactionRuleMapper;

    @Value("${novel.rag.reaction-rule-index-name:reaction_rule}")
    private String reactionRuleIndexName;

    public int upsertDocuments(List<RoleReactionRule> rules) {
        List<RagDocument> documents = rules.stream()
                .map(this::toDocument)
                .toList();
        return ragService.upsertDocuments(reactionRuleIndexName, documents);
    }

    public int deleteByIds(Long characterId, List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return 0;
        }
        List<String> documentIds = ruleIds.stream()
                .map(ruleId -> ruleDocumentId(characterId, ruleId))
                .toList();
        return ragService.deleteDocuments(reactionRuleIndexName, documentIds);
    }

    /**
     * 按规则主键写入向量库，并回写索引状态。
     *
     * @param ruleIds 待索引规则主键
     * @return 成功写入向量库的文档数量
     */
    public int indexByIds(List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return 0;
        }
        List<RoleReactionRule> rules = roleReactionRuleMapper.selectList(new LambdaQueryWrapper<RoleReactionRule>()
                .in(RoleReactionRule::getId, ruleIds)
                .in(RoleReactionRule::getVectorStatus, VECTOR_PENDING, VECTOR_FAILED));
        if (rules.isEmpty()) {
            log.info("没有需要索引的角色反应规则，requestCount: {}", ruleIds.size());
            return 0;
        }
        try {
            int indexedCount = upsertDocuments(rules);
            if (indexedCount < 0) {
                markFailed(rules, "RAG服务降级，角色反应规则未写入向量库");
                log.warn("RAG服务降级，角色反应规则未写入向量库，requestCount: {}", rules.size());
                return indexedCount;
            }
            if (indexedCount != rules.size()) {
                throw new IllegalStateException("角色反应规则向量索引数量不一致，requestCount: "
                        + rules.size() + ", indexedCount: " + indexedCount);
            }
            rules.forEach(rule -> {
                rule.setVectorStatus(VECTOR_INDEXED);
                rule.setVectorError(null);
                rule.setIndexedTime(LocalDateTime.now());
            });
            updateById(rules);
            return indexedCount;
        } catch (RuntimeException e) {
            markFailed(rules, e.getMessage());
            throw e;
        }
    }

    private void markFailed(List<RoleReactionRule> rules, String errorMessage) {
        rules.forEach(rule -> {
            rule.setVectorStatus(VECTOR_FAILED);
            rule.setVectorError(errorMessage);
        });
        updateById(rules);
    }

    private void updateById(List<RoleReactionRule> rules) {
        rules.forEach(roleReactionRuleMapper::updateById);
    }

    private RagDocument toDocument(RoleReactionRule rule) {
        RagDocument document = new RagDocument();
        document.setDocumentId(ruleDocumentId(rule.getCharacterId(), rule.getId()));
        document.setContent(rule.getSituation() + "\n" + rule.getRule());
        document.setMetadata(metadata(rule));
        return document;
    }

    private String ruleDocumentId(Long characterId, Long ruleId) {
        return "character:%s:rule:%s".formatted(characterId, ruleId);
    }

    private Map<String, Object> metadata(RoleReactionRule rule) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("character_id", rule.getCharacterId());
        metadata.put("character_name", rule.getCharacterName());
        metadata.put("rule_id", rule.getId());
        metadata.put("situation", rule.getSituation());
        return metadata;
    }
}
