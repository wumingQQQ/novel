package com.wuming.novel.llm.role;

import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.dto.PassageCharacterResult;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.llm.parser.LlmJsonResponseParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PassageCharacterRecognitionService {
    private static final String TEMPLATE_PATH = "prompts/role-runtime/passage-character-recognition.st";

    private final LlmClientFactory clientFactory;
    private final LlmJsonResponseParser responseParser;
    private final PromptTemplateRenderer promptTemplateRenderer;

    public PassageCharacterResult recognize(NovelPassage passage) {
        String prompt = promptTemplateRenderer.render(TEMPLATE_PATH, Map.of(
                "passageContent", passage.getContent() == null ? "" : passage.getContent()
        ));
        String raw = clientFactory.defaultClient()
                .prompt()
                .user(prompt)
                .call()
                .content();
        return responseParser.parse(raw, PassageCharacterResult.class);
    }
}
