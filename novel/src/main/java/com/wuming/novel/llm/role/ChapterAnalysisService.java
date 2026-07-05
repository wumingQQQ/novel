package com.wuming.novel.llm.role;

import com.wuming.novel.domain.dto.ChapterAnalysisResult;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.llm.parser.LlmJsonResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterAnalysisService {
    private static final int CONTENT_LIMIT = 12000;
    private static final String TEMPLATE_PATH = "prompts/role-runtime/chapter-analysis.st";

    private final ChatClient chatClient;
    private final LlmJsonResponseParser responseParser;
    private final PromptTemplateRenderer promptTemplateRenderer;

    public ChapterAnalysisResult analyze(Chapter chapter) {
        String prompt = promptTemplateRenderer.render(TEMPLATE_PATH, Map.of(
                "chapterTitle", safeString(chapter.getTitle()),
                "chapterContent", abbreviate(chapter.getContent())
        ));
        String raw = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
        return responseParser.parse(raw, ChapterAnalysisResult.class);
    }

    private String abbreviate(String content) {
        if (content == null || content.length() <= CONTENT_LIMIT) {
            return content == null ? "" : content;
        }
        return content.substring(0, CONTENT_LIMIT);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
