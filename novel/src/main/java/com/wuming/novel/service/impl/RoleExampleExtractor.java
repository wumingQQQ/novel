package com.wuming.novel.service.impl;

import com.wuming.novel.domain.dto.RoleExampleExtractionResult;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.llm.LlmConcurrencyLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 从单个Passage中抽取并转换角色原作样本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleExampleExtractor {
    private static final String SYSTEM_TEMPLATE_PATH = "prompts/system/role-example-extraction.st";
    private static final String USER_TEMPLATE_PATH = "prompts/user/role-example-extraction.st";
    private static final String VECTOR_PENDING = "PENDING";
    private static final Set<String> SAMPLE_TYPES = Set.of("INTERACTION");

    private final ChatClient chatClient;
    private final PromptTemplateRenderer renderer;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;

    @Value("${novel.role-example.min-confidence:0.6}")
    private double minConfidence;

    /**
     * 在统一并发限制下调用LLM，并将有效响应转换为待保存样本。
     *
     * @param character 目标角色
     * @param passage 原文文本块
     * @return 通过类型、文本及置信度校验的样本
     */
    public List<RoleExample> extract(RoleCharacter character, NovelPassage passage) {
        return llmConcurrencyLimiter.execute(() -> {
            PromptTemplateRenderer.DualPrompt dualPrompt = renderer.renderDual(
                    SYSTEM_TEMPLATE_PATH,
                    USER_TEMPLATE_PATH,
                    Map.of(
                            "characterName", character.getCharacterName(),
                            "passageContent", passage.getContent()
                    )
            );
            RoleExampleExtractionResult result = chatClient.prompt()
                    .system(dualPrompt.systemPrompt())
                    .user(dualPrompt.userPrompt())
                    .call()
                    .entity(RoleExampleExtractionResult.class);
            if (result == null || result.examples().isEmpty()) {
                log.debug("Passage未抽取到角色样本，characterId: {}, passageId: {}",
                        character.getId(), passage.getId());
                return List.of();
            }

            List<RoleExample> examples = result.examples().stream()
                    .map(example -> toRoleExample(character, passage, example))
                    .filter(Objects::nonNull)
                    .toList();
            log.debug("Passage角色样本抽取完成，characterId: {}, passageId: {}, extractedCount: {}",
                    character.getId(), passage.getId(), examples.size());
            return examples;
        });
    }

    /**
     * 将单个LLM响应转换为角色样本，无效内容返回null。
     */
    private RoleExample toRoleExample(RoleCharacter character,
                                      NovelPassage passage,
                                      RoleExampleExtractionResult.Example example) {
        String sampleType = normalizeSampleType(example.type());
        String sampleText = normalizeSampleText(example.sampleText());
        double confidence = example.confidence() == null ? 0.0 : example.confidence();
        if (sampleType == null || sampleText == null || confidence < minConfidence) {
            return null;
        }

        RoleExample roleExample = new RoleExample();
        roleExample.setCharacterId(character.getId());
        roleExample.setCharacterName(character.getCharacterName());
        roleExample.setPassageId(passage.getId());
        roleExample.setSampleType(sampleType);
        roleExample.setSampleText(sampleText);
        roleExample.setConfidence(confidence);
        roleExample.setVectorStatus(VECTOR_PENDING);
        return roleExample;
    }

    /**
     * 归一化样本类型，模型返回不支持的类型时忽略该样本。
     */
    private String normalizeSampleType(String sampleType) {
        if (sampleType == null) {
            return null;
        }
        String normalized = sampleType.trim().toUpperCase();
        return SAMPLE_TYPES.contains(normalized) ? normalized : null;
    }

    /**
     * 清理样本文本，空文本不参与保存。
     */
    private String normalizeSampleText(String sampleText) {
        if (sampleText == null) {
            return null;
        }
        String normalized = sampleText.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
