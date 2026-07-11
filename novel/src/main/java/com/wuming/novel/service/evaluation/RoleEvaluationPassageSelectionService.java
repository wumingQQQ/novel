package com.wuming.novel.service.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.api.rag.dto.RagDocument;
import com.wuming.api.rag.dto.SearchHit;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleEvaluation;
import com.wuming.novel.domain.entity.RoleEvaluationCase;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.domain.entity.RoleProfile;
import com.wuming.novel.infrastructure.mapper.NovelPassageMapper;
import com.wuming.novel.infrastructure.mapper.RoleEvaluationCaseMapper;
import com.wuming.novel.infrastructure.mapper.RoleExampleMapper;
import com.wuming.novel.integration.rpc.rag.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 为角色评测选择兼具随机性与角色相关性的原作 Passage。
 */
@Service
@RequiredArgsConstructor
public class RoleEvaluationPassageSelectionService {
    private final RoleExampleMapper exampleMapper;
    private final NovelPassageMapper passageMapper;
    private final RoleEvaluationCaseMapper caseMapper;
    private final UserRoleProfileService profileService;
    private final RagService ragService;

    @Value("${novel.role-evaluation.case-candidate-pool-size:24}")
    private int candidatePoolSize = 24;

    @Value("${novel.role-evaluation.case-candidate-pool-multiplier:4}")
    private int candidatePoolMultiplier = 4;

    /**
     * 排除当前评测已使用 Passage 后，从随机候选池中通过 rerank 选择案例来源。
     *
     * @param evaluation 当前独立评测
     * @param character 已完成构建的目标角色
     * @param limit 最终需要的 Passage 数量
     * @return 按重排序优先级排列的 Passage 及其角色样本
     */
    public List<SelectedPassage> select(RoleEvaluation evaluation, RoleCharacter character, int limit) {
        int selectedCount = Math.max(1, limit);
        Set<Long> usedPassageIds = findUsedPassageIds(evaluation.getId());
        List<SelectedPassage> candidates = findCandidates(character.getId(), usedPassageIds);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Collections.shuffle(candidates);
        List<SelectedPassage> randomPool = candidates.subList(0,
                Math.min(candidates.size(), Math.max(candidatePoolSize,
                        selectedCount * Math.max(1, candidatePoolMultiplier))));
        List<SelectedPassage> ranked = rerank(randomPool, evaluation, character);
        return ranked.subList(0, Math.min(selectedCount, ranked.size()));
    }

    /**
     * 查询本评测已经生成过案例的 Passage，避免重复抽题。
     *
     * @param evaluationId 独立评测主键
     * @return 已使用的 Passage 主键集合
     */
    private Set<Long> findUsedPassageIds(Long evaluationId) {
        return caseMapper.selectList(new LambdaQueryWrapper<RoleEvaluationCase>()
                        .eq(RoleEvaluationCase::getEvaluationId, evaluationId))
                .stream()
                .map(RoleEvaluationCase::getPassageId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 将角色互动样本按 Passage 聚合，并读取可用原文内容。
     *
     * @param characterId 目标角色主键
     * @param usedPassageIds 已用于当前评测的 Passage 主键
     * @return 可参与随机候选池的 Passage
     */
    private List<SelectedPassage> findCandidates(Long characterId, Set<Long> usedPassageIds) {
        List<RoleExample> examples = exampleMapper.selectList(new LambdaQueryWrapper<RoleExample>()
                .eq(RoleExample::getCharacterId, characterId)
                .eq(RoleExample::getSampleType, "INTERACTION")
                .eq(RoleExample::getVectorStatus, "INDEXED"));
        Map<Long, List<RoleExample>> examplesByPassage = new LinkedHashMap<>();
        for (RoleExample example : examples) {
            if (example.getPassageId() == null || usedPassageIds.contains(example.getPassageId())) {
                continue;
            }
            examplesByPassage.computeIfAbsent(example.getPassageId(), ignored -> new ArrayList<>()).add(example);
        }
        List<SelectedPassage> candidates = new ArrayList<>();
        for (Map.Entry<Long, List<RoleExample>> entry : examplesByPassage.entrySet()) {
            NovelPassage passage = passageMapper.selectById(entry.getKey());
            if (passage != null && passage.getContent() != null && !passage.getContent().isBlank()) {
                candidates.add(new SelectedPassage(passage, entry.getValue()));
            }
        }
        return candidates;
    }

    /**
     * 对随机候选池重排序；RAG 服务不可用时保留随机顺序，确保案例构造流程仍可继续。
     *
     * @param randomPool 随机候选 Passage
     * @param evaluation 当前独立评测
     * @param character 目标角色
     * @return 重排序后的候选 Passage
     */
    private List<SelectedPassage> rerank(List<SelectedPassage> randomPool,
                                         RoleEvaluation evaluation,
                                         RoleCharacter character) {
        List<RagDocument> documents = randomPool.stream().map(this::toRerankDocument).toList();
        List<SearchHit> hits = ragService.rerankDocuments(buildRerankQuery(evaluation, character), documents);
        if (hits.isEmpty()) {
            return new ArrayList<>(randomPool);
        }
        Map<Long, SelectedPassage> candidatesById = randomPool.stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.passage().getId(), item -> item, (left, ignored) -> left, LinkedHashMap::new));
        List<SelectedPassage> ranked = new ArrayList<>();
        for (SearchHit hit : hits) {
            Long passageId = toLong(hit.getMetadata().get("passage_id"));
            SelectedPassage selected = passageId == null ? null : candidatesById.remove(passageId);
            if (selected != null) {
                ranked.add(selected);
            }
        }
        // Rerank 服务可返回不完整结果，剩余候选保持随机顺序补足。
        ranked.addAll(candidatesById.values());
        return ranked;
    }

    /**
     * 将 Passage 转换为仅用于本次有限集合重排序的 RAG 文档。
     *
     * @param selected 待重排序 Passage
     * @return RAG 文档
     */
    private RagDocument toRerankDocument(SelectedPassage selected) {
        RagDocument document = new RagDocument();
        document.setDocumentId("evaluation-passage:" + selected.passage().getId());
        document.setContent(selected.passage().getContent());
        document.setMetadata(Map.of("passage_id", selected.passage().getId()));
        return document;
    }

    /**
     * 使用角色身份与有效画像构造 rerank 查询，避免只按角色名称匹配。
     *
     * @param evaluation 当前独立评测
     * @param character 目标角色
     * @return 角色相关性查询文本
     */
    private String buildRerankQuery(RoleEvaluation evaluation, RoleCharacter character) {
        RoleProfile profile = profileService.loadEffectiveProfile(
                character.getId(), evaluation.getUserRoleVersionId());
        return "小说《%s》角色「%s」。核心性格：%s。请选择最能体现该角色互动、冲突回应和语言风格的原作片段。"
                .formatted(character.getNovelName(), character.getCharacterName(),
                        profile.getCoreTraits() == null ? "" : profile.getCoreTraits());
    }

    /**
     * 将 RAG 元数据中的 Passage 主键转换为 Long。
     *
     * @param value 元数据值
     * @return Passage 主键；无法转换时返回 null
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
     * 已选 Passage 与同源角色样本，用于后续构造案例审计快照。
     *
     * @param passage 来源原文块
     * @param examples 同一 Passage 下的互动样本
     */
    public record SelectedPassage(NovelPassage passage, List<RoleExample> examples) {
    }
}
