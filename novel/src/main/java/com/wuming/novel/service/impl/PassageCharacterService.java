package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.dto.PassageCharacterResult;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.PassageCharacter;
import com.wuming.novel.infrastructure.mapper.PassageCharacterMapper;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.llm.LlmConcurrencyLimiter;
import com.wuming.novel.service.IPassageCharacterService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.Map;

/**
 * Passage 出场角色映射基础服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassageCharacterService
        extends ServiceImpl<PassageCharacterMapper, PassageCharacter>
        implements IPassageCharacterService {
    private static final String SYSTEM_TEMPLATE_PATH = "prompts/system/passage-character-recognition.st";
    private static final String USER_TEMPLATE_PATH = "prompts/user/passage-character-recognition.st";

    private final ChatClient chatClient;
    private final PromptTemplateRenderer renderer;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;
    @Resource(name = "llmExecutor")
    private Executor llmExecutor;

    /**
     * 识别 Passage 中出场人物，并保存 Passage 与人物映射。
     *
     * @param passages 已落库并带有id的 Passage 列表
     */
    @Override
    public void recognizeAndSave(List<NovelPassage> passages) {
        if (passages == null || passages.isEmpty()) {
            return;
        }
        List<CompletableFuture<List<PassageCharacter>>> futures = passages.stream()
                .map(passage -> CompletableFuture.supplyAsync(
                        () -> llmConcurrencyLimiter.execute(
                                () -> passageCharacter(passage.getId(), recognizeOnePassage(passage))),
                        llmExecutor))
                .toList();
        List<PassageCharacter> passageCharacters = futures.stream()
                .flatMap(future -> future.exceptionally(e -> {
                    log.warn("Passage人物识别任务执行失败", e);
                    return List.of();
                }).join().stream())
                .toList();
        if (!passageCharacters.isEmpty()) {
            saveBatch(passageCharacters);
        }
    }

    private List<String> recognizeOnePassage(NovelPassage passage) {
        try {
            PassageCharacterResult result = recognize(passage);
            return normalizeCharacters(result.characters());
        } catch (RuntimeException e) {
            log.warn("Passage人物识别失败，passageId: {}, chapterId: {}, chapterSequence: {}",
                    passage.getId(), passage.getChapterId(), passage.getInnerSequence(), e);
            return List.of();
        }
    }

    private PassageCharacterResult recognize(NovelPassage passage) {
        PromptTemplateRenderer.DualPrompt dualPrompt = renderer.renderDual(
                SYSTEM_TEMPLATE_PATH,
                USER_TEMPLATE_PATH,
                Map.of("passageContent", passage.getContent())
        );
        return chatClient.prompt()
                .system(dualPrompt.systemPrompt())
                .user(dualPrompt.userPrompt())
                .call()
                .entity(PassageCharacterResult.class);
    }

    private List<String> normalizeCharacters(List<String> characters) {
        if (characters == null) {
            return List.of();
        }
        return characters.stream()
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    private List<PassageCharacter> passageCharacter(Long passageId, List<String> characterName) {
        return characterName.stream()
                .map(name -> new PassageCharacter(passageId, name))
                .toList();
    }
}
