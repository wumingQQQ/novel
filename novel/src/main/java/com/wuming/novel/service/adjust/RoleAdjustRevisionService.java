package com.wuming.novel.service.adjust;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.wuming.novel.domain.dto.ReviseRoleAdjustResult;
import com.wuming.novel.domain.dto.RoleAdjustRevisionCandidate;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.RoleAdjustEvidence;
import com.wuming.novel.domain.entity.RoleAdjustItem;
import com.wuming.novel.domain.entity.RoleAdjustRequest;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.enums.RoleAdjustChangeType;
import com.wuming.novel.domain.enums.RoleAdjustRequestStatus;
import com.wuming.novel.domain.enums.RoleAdjustStatus;
import com.wuming.novel.infrastructure.mapper.RoleAdjustEvidenceMapper;
import com.wuming.novel.infrastructure.mapper.RoleAdjustItemMapper;
import com.wuming.novel.infrastructure.mapper.RoleAdjustRequestMapper;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.llm.LlmConcurrencyLimiter;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.impl.NovelPassageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 角色调整候选项修订服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAdjustRevisionService {
    private static final String SYSTEM_TEMPLATE_PATH = "prompts/system/role-adjust-candidate-revise.st";
    private static final String USER_TEMPLATE_PATH = "prompts/user/role-adjust-candidate-revise.st";
    private static final int REVISION_ERROR_LIMIT = 500;

    private final RoleAdjustRequestMapper requestMapper;
    private final RoleAdjustItemMapper itemMapper;
    private final RoleAdjustEvidenceMapper evidenceMapper;
    private final IRoleCharacterService roleCharacterService;
    private final NovelPassageService passageService;
    private final ChatClient chatClient;
    private final PromptTemplateRenderer renderer;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;

    /**
     * 修订当前用户请求下所有处于 REVISING 状态的候选项。
     *
     * <p>单个候选项修订失败时只记录该项错误，不影响其他候选项继续修订。</p>
     */
    public ReviseRoleAdjustResult revise(Long userId, Long requestId) {
        RoleAdjustRequest request = requireRevisableRequest(userId, requestId);
        ReviseRoleAdjustResult result = new ReviseRoleAdjustResult();
        result.setRequestId(requestId);

        List<RoleAdjustItem> items = loadRevisingItems(requestId);
        if (items.isEmpty()) {
            return result;
        }

        RoleCharacter character = requireCharacter(request.getCharacterId());
        for (RoleAdjustItem item : items) {
            try {
                reviseItem(character, item);
                result.getRevisedItemIds().add(item.getId());
            } catch (RuntimeException exception) {
                String message = errorMessage(exception);
                markRevisionError(item.getId(), message);
                result.getItemErrors().add(new ReviseRoleAdjustResult.ItemError(item.getId(), message));
                log.debug("角色调整候选修订失败，requestId: {}, itemId: {}, errorType: {}, errorMessage: {}",
                        requestId, item.getId(), exception.getClass().getSimpleName(), message, exception);
            }
        }
        return result;
    }

    /**
     * 校验请求存在、属于当前用户且仍处于可评审/可修订状态。
     */
    private RoleAdjustRequest requireRevisableRequest(Long userId, Long requestId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("requestId不能为空");
        }
        RoleAdjustRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("角色调整请求不存在: " + requestId);
        }
        if (!Objects.equals(request.getUserId(), userId)) {
            throw new IllegalStateException("角色调整请求不属于当前用户");
        }
        if (request.getStatus() != RoleAdjustRequestStatus.READY) {
            throw new IllegalStateException("只有READY状态的角色调整请求可以修订候选项");
        }
        return request;
    }

    /**
     * 读取需要按用户反馈重新生成的候选项。
     */
    private List<RoleAdjustItem> loadRevisingItems(Long requestId) {
        List<RoleAdjustItem> items = itemMapper.selectList(new LambdaQueryWrapper<RoleAdjustItem>()
                .eq(RoleAdjustItem::getRequestId, requestId)
                .eq(RoleAdjustItem::getStatus, RoleAdjustStatus.REVISING)
                .orderByAsc(RoleAdjustItem::getDisplayOrder));
        return items == null ? List.of() : items;
    }

    /**
     * 校验角色仍存在，并为提示词补充角色名和小说名。
     */
    private RoleCharacter requireCharacter(Long characterId) {
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            throw new IllegalArgumentException("角色不存在: " + characterId);
        }
        return character;
    }

    /**
     * 修订单个候选项，成功后覆盖原候选内容并重新置为 PENDING。
     */
    private void reviseItem(RoleCharacter character, RoleAdjustItem item) {
        List<NovelPassage> evidences = loadEvidencePassages(character, item);
        PromptTemplateRenderer.DualPrompt dualPrompt = renderer.renderDual(
                SYSTEM_TEMPLATE_PATH,
                USER_TEMPLATE_PATH,
                Map.of(
                        "characterName", blankToDefault(character.getCharacterName(), "未知角色"),
                        "novelName", blankToDefault(character.getNovelName(), "未知小说"),
                        "changeType", blankToDefault(item.getChangeType(), "未知"),
                        "targetAdjustmentId", blankToDefault(item.getTargetAdjustmentId(), "无"),
                        "applicability", blankToDefault(item.getApplicability(), ""),
                        "expectedBehavior", blankToDefault(item.getExpectedBehavior(), ""),
                        "forbiddenBehavior", blankToDefault(item.getForbiddenBehavior(), ""),
                        "revisionFeedback", blankToDefault(item.getRevisionFeedback(), ""),
                        "evidences", formatEvidences(evidences)
                )
        );
        RoleAdjustRevisionCandidate revised = llmConcurrencyLimiter.execute(() -> chatClient.prompt()
                .system(dualPrompt.systemPrompt())
                .user(dualPrompt.userPrompt())
                .call()
                .entity(RoleAdjustRevisionCandidate.class));
        validateRevisedCandidate(revised);
        overwriteItem(item.getId(), revised);
    }

    /**
     * 按候选项现有证据关系回查原作 Passage，修订时不重新扩展证据范围。
     */
    private List<NovelPassage> loadEvidencePassages(RoleCharacter character, RoleAdjustItem item) {
        List<RoleAdjustEvidence> evidences = evidenceMapper.selectList(new LambdaQueryWrapper<RoleAdjustEvidence>()
                .eq(RoleAdjustEvidence::getItemId, item.getId()));
        LinkedHashSet<Long> passageIds = new LinkedHashSet<>();
        if (evidences != null) {
            for (RoleAdjustEvidence evidence : evidences) {
                if (evidence.getPassageIds() != null) {
                    passageIds.addAll(evidence.getPassageIds());
                }
            }
        }
        if (passageIds.isEmpty()) {
            throw new IllegalStateException("候选项缺少原作证据");
        }

        List<NovelPassage> passages = passageService.lambdaQuery()
                .eq(NovelPassage::getNovelId, character.getNovelId())
                .in(NovelPassage::getId, passageIds)
                .list();
        Map<Long, NovelPassage> passagesById = new LinkedHashMap<>();
        for (NovelPassage passage : passages == null ? List.<NovelPassage>of() : passages) {
            passagesById.put(passage.getId(), passage);
        }
        List<NovelPassage> orderedPassages = passageIds.stream()
                .map(passagesById::get)
                .filter(Objects::nonNull)
                .toList();
        if (orderedPassages.isEmpty()) {
            throw new IllegalStateException("候选项原作证据不存在");
        }
        return orderedPassages;
    }

    /**
     * 校验模型返回结构，避免无效修订覆盖原候选。
     */
    private void validateRevisedCandidate(RoleAdjustRevisionCandidate revised) {
        if (revised == null) {
            throw new IllegalStateException("LLM修订结果为空");
        }
        if (revised.changeType() == null) {
            throw new IllegalStateException("LLM修订结果缺少调整类型");
        }
        if (isBlank(revised.applicability()) || isBlank(revised.expectedBehavior())) {
            throw new IllegalStateException("LLM修订结果缺少适用场景或期望行为");
        }
    }

    /**
     * 使用修订结果覆盖原候选项，并清理用户改写反馈和上一次修订错误。
     */
    private void overwriteItem(Long itemId, RoleAdjustRevisionCandidate revised) {
        RoleAdjustItem update = new RoleAdjustItem();
        update.setId(itemId);
        update.setChangeType(revised.changeType());
        update.setTargetAdjustmentId(resolveTargetAdjustmentId(revised));
        update.setApplicability(revised.applicability());
        update.setExpectedBehavior(revised.expectedBehavior());
        update.setForbiddenBehavior(blankToNull(revised.forbiddenBehavior()));
        update.setStatus(RoleAdjustStatus.PENDING);
        update.setRevisionFeedback(null);
        update.setRevisionError(null);
        itemMapper.updateById(update);
        clearRevisionState(itemId);
    }

    /**
     * MyBatis-Plus 默认不会通过 updateById 写入 null，这里显式清理修订状态字段。
     */
    private void clearRevisionState(Long itemId) {
        itemMapper.update(null, new UpdateWrapper<RoleAdjustItem>()
                .eq("id", itemId)
                .set("revision_feedback", null)
                .set("revision_error", null));
    }

    /**
     * 记录单个候选项修订失败原因，状态仍保持 REVISING 等待用户或系统再次处理。
     */
    private void markRevisionError(Long itemId, String message) {
        RoleAdjustItem update = new RoleAdjustItem();
        update.setId(itemId);
        update.setRevisionError(message);
        itemMapper.updateById(update);
    }

    /**
     * 将原作 Passage 格式化为修订提示词证据。
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

    private String resolveTargetAdjustmentId(RoleAdjustRevisionCandidate revised) {
        if (revised.changeType() == RoleAdjustChangeType.ADD) {
            return null;
        }
        return blankToNull(revised.targetAdjustmentId());
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > REVISION_ERROR_LIMIT ? message.substring(0, REVISION_ERROR_LIMIT) : message;
    }

    private String blankToDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString();
        return text.isBlank() ? defaultValue : text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
