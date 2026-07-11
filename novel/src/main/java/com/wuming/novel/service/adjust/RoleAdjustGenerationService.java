package com.wuming.novel.service.adjust;

import com.wuming.api.rag.dto.SearchHit;
import com.wuming.novel.domain.dto.RoleAdjustCandidate;
import com.wuming.novel.domain.dto.RoleAdjustCandidateResult;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.PersonalRoleVersion;
import com.wuming.novel.domain.entity.RoleAdjustRequest;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.integration.rpc.rag.RoleExampleVectorIndexService;
import com.wuming.novel.infrastructure.mapper.PersonalRoleVersionMapper;
import com.wuming.novel.llm.LlmConcurrencyLimiter;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.impl.NovelPassageService;
import com.wuming.novel.service.impl.RoleExampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAdjustGenerationService {
    private static final String SYSTEM_TEMPLATE_PATH = "prompts/system/role-adjust-candidate-generate.st";
    private static final String USER_TEMPLATE_PATH = "prompts/user/role-adjust-candidate-generate.st";

    private final RoleExampleService  roleExampleService;
    private final RoleExampleVectorIndexService exampleRagService;
    private final NovelPassageService passageService;
    private final IRoleCharacterService roleCharacterService;
    private final PersonalRoleVersionMapper roleVersionMapper;
    private final ChatClient chatClient;
    private final PromptTemplateRenderer renderer;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;

    @Value("${novel.role-adjust.role-example-top-k:20}")
    private int roleExampleTopK = 20;

    @Value("${novel.role-adjust.role-example-top-n:8}")
    private int roleExampleTopN = 8;

    /**
     * 基于用户请求生成候选调整项。
     *
     * <p>该方法只返回 LLM 候选结果，不写入 role_adjust_items，也不修改请求状态。</p>
     */
    public List<RoleAdjustCandidate> generateCandidates(RoleAdjustRequest request) {
        validateRequest(request);
        RoleCharacter character = requireCharacter(request.getCharacterId());
        List<NovelPassage> evidences = loadEvidence(request, character);
        if (evidences.isEmpty()) {
            log.debug("角色调整候选生成跳过，characterId: {}, reason: {}", request.getCharacterId(), "没有可用原作证据");
            return List.of();
        }

        PromptTemplateRenderer.DualPrompt dualPrompt = renderer.renderDual(
                SYSTEM_TEMPLATE_PATH,
                USER_TEMPLATE_PATH,
                Map.of(
                        "characterName", blankToDefault(character.getCharacterName(), "未知角色"),
                        "novelName", blankToDefault(character.getNovelName(), "未知小说"),
                        "requirement", request.getRequirement(),
                        "chatText", blankToDefault(request.getChatText(), "无"),
                        "baselineAdjustments", formatBaselineAdjustments(loadBaselineAdjustments(request)),
                        "evidences", formatEvidences(evidences)
                )
        );
        RoleAdjustCandidateResult result = llmConcurrencyLimiter.execute(() -> chatClient.prompt()
                .system(dualPrompt.systemPrompt())
                .user(dualPrompt.userPrompt())
                .call()
                .entity(RoleAdjustCandidateResult.class));
        return result == null ? List.of() : result.candidates();
    }

    /**
     * 从角色样本召回中回查同一小说的原作 Passage，并保留 RAG 的相关性顺序。
     */
    private List<NovelPassage> loadEvidence(RoleAdjustRequest request) {
        return loadEvidence(request, requireCharacter(request.getCharacterId()));
    }

    /**
     * 从角色样本召回中回查同一小说的原作 Passage，并保留 RAG 的相关性顺序。
     */
    private List<NovelPassage> loadEvidence(RoleAdjustRequest request, RoleCharacter character) {
        // 根据用户需求召回example
        List<SearchHit> hits = exampleRagService.search(
                request.getCharacterId(),
                request.getRequirement(),
                roleExampleTopK,
                true,
                roleExampleTopN
        );

        List<Long> roleExampleIds = (hits == null ? List.<SearchHit>of() : hits).stream()
                .map(hit -> parseExampleId(hit, request.getCharacterId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleExampleIds.isEmpty()) {
            return List.of();
        }

        List<RoleExample> examples = roleExampleService.lambdaQuery()
                .eq(RoleExample::getCharacterId, request.getCharacterId())
                .in(RoleExample::getId, roleExampleIds)
                .list();
        Map<Long, RoleExample> examplesById = new LinkedHashMap<>();
        for (RoleExample example : examples) {
            if (Objects.equals(example.getCharacterId(), request.getCharacterId())) {
                examplesById.put(example.getId(), example);
            }
        }
        LinkedHashSet<Long> passageIds = new LinkedHashSet<>();
        for (Long exampleId : roleExampleIds) {
            RoleExample example = examplesById.get(exampleId);
            if (example != null && example.getPassageId() != null) {
                passageIds.add(example.getPassageId());
            }
        }
        if (passageIds.isEmpty()) {
            return List.of();
        }

        List<NovelPassage> passages = passageService.lambdaQuery()
                .eq(NovelPassage::getNovelId, character.getNovelId())
                .in(NovelPassage::getId, passageIds)
                .list();

        // 获取按照顺序排列的passage
        Map<Long, NovelPassage> passagesById = new LinkedHashMap<>();
        for (NovelPassage passage : passages) {
            if (Objects.equals(passage.getNovelId(), character.getNovelId())) {
                passagesById.put(passage.getId(), passage);
            }
        }
        return passageIds.stream()
                .map(passagesById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 校验本次生成所需的最小请求字段。
     */
    private void validateRequest(RoleAdjustRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("调整请求不能为空");
        }
        if (request.getCharacterId() == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        if (request.getRequirement() == null || request.getRequirement().isBlank()) {
            throw new IllegalArgumentException("调整要求不能为空");
        }
    }

    /**
     * 校验角色仍存在，避免对已删除角色的历史请求继续生成候选项。
     */
    private RoleCharacter requireCharacter(Long characterId) {
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            throw new IllegalArgumentException("角色不存在: " + characterId);
        }
        return character;
    }

    /**
     * 仅接受当前角色对应的 RoleExample 向量文档标识。
     */
    private Long parseExampleId(SearchHit hit, Long characterId) {
        if (hit == null || hit.getDocumentId() == null) {
            return null;
        }
        String prefix = "character:" + characterId + ":example:";
        String documentId = hit.getDocumentId();
        if (!documentId.startsWith(prefix)) {
            return null;
        }
        try {
            long exampleId = Long.parseLong(documentId.substring(prefix.length()));
            return exampleId > 0 ? exampleId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 将个人版本的有效调整快照转换为本次提示词可引用的连续序号。
     */
    private List<BaselineAdjustment> loadBaselineAdjustments(RoleAdjustRequest request) {
        if (request.getBaseVersionId() == null) {
            return List.of();
        }
        PersonalRoleVersion version = roleVersionMapper.selectById(request.getBaseVersionId());
        if (version == null) {
            throw new IllegalArgumentException("个人角色版本不存在: " + request.getBaseVersionId());
        }
        List<PersonalRoleVersion.BehaviorAdjustmentSnapshot> snapshots =
                version.getBehaviorAdjustmentsSnapshot();
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<PersonalRoleVersion.BehaviorAdjustmentSnapshot> orderedSnapshots = snapshots.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PersonalRoleVersion.BehaviorAdjustmentSnapshot::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PersonalRoleVersion.BehaviorAdjustmentSnapshot::getAdjustmentId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return java.util.stream.IntStream.range(0, orderedSnapshots.size())
                .mapToObj(index -> toBaselineAdjustment(index + 1, orderedSnapshots.get(index)))
                .toList();
    }

    /**
     * 保留稳定调整标识与行为文本，供模型通过临时引用提出替换或停用建议。
     */
    private BaselineAdjustment toBaselineAdjustment(int ref,
                                                     PersonalRoleVersion.BehaviorAdjustmentSnapshot snapshot) {
        return new BaselineAdjustment(ref, snapshot.getAdjustmentId(), snapshot.getApplicability(),
                snapshot.getExpectedBehavior(), snapshot.getForbiddenBehavior());
    }

    /**
     * 将基线补丁格式化为稳定编号，供模型在 REPLACE/DISABLE 时引用。
     */
    private String formatBaselineAdjustments(List<BaselineAdjustment> adjustments) {
        if (adjustments == null || adjustments.isEmpty()) {
            return "无，当前基于公共角色直接调整";
        }
        StringJoiner joiner = new StringJoiner("\n\n");
        for (BaselineAdjustment adjustment : adjustments) {
            joiner.add("baselineRef=" + adjustment.ref()
                    + "\nadjustmentId=" + blankToDefault(adjustment.adjustmentId(), "无")
                    + "\n适用场景：" + blankToDefault(adjustment.applicability(), "未填写")
                    + "\n期望行为：" + blankToDefault(adjustment.expectedBehavior(), "未填写")
                    + "\n禁止行为：" + blankToDefault(adjustment.forbiddenBehavior(), "未填写"));
        }
        return joiner.toString();
    }

    /**
     * 将原作 Passage 格式化为模型可引用的 evidenceRef 列表。
     */
    private String formatEvidences(List<NovelPassage> passages) {
        StringJoiner joiner = new StringJoiner("\n\n");
        for (int i = 0; i < passages.size(); i++) {
            NovelPassage passage = passages.get(i);
            joiner.add("evidenceRef=" + (i + 1)
                    + "\npassageId=" + passage.getId()
                    + "\nsequence=" + blankToDefault(passage.getSequence(), "未知")
                    + "\n原文：\n" + blankToDefault(passage.getContent(), ""));
        }
        return joiner.toString();
    }

    private String blankToDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString();
        return text.isBlank() ? defaultValue : text;
    }

    /**
     * 单次候选生成中传递给模型的既有行为调整投影。
     */
    record BaselineAdjustment(
            int ref,
            String adjustmentId,
            String applicability,
            String expectedBehavior,
            String forbiddenBehavior) {
    }
}
