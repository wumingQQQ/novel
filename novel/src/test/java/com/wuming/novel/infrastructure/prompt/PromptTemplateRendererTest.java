package com.wuming.novel.infrastructure.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateRendererTest {

    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    @Test
    void shouldRenderChapterAnalysisTemplateFromResources() {
        String prompt = renderer.render("prompts/role-runtime/chapter-analysis.st", Map.of(
                "chapterTitle", "第一章 测试",
                "chapterContent", "第一段内容"
        ));

        assertThat(prompt).contains("章节标题：第一章 测试");
        assertThat(prompt).contains("章节内容：\n第一段内容");
        assertThat(prompt).contains("\"summary\"");
    }
}
