package com.wuming.novel.llm.role;

import com.wuming.novel.domain.dto.PassageCharacterResult;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PassageCharacterRecognitionService {
    private static final String TEMPLATE_PATH = "prompts/role-runtime/passage-character-recognition.st";

    private final ChatClient chatClient;
    private final PromptTemplateRenderer promptTemplateRenderer;

    public PassageCharacterResult recognize(NovelPassage passage) {
        String prompt = promptTemplateRenderer.render(TEMPLATE_PATH, Map.of(
                "passageContent", passage.getContent() == null ? "" : passage.getContent()
        ));
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .entity(PassageCharacterResult.class);
    }
}
