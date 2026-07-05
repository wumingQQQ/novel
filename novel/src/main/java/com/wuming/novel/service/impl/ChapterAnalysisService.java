package com.wuming.novel.service.impl;

import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.dto.ChapterAnalysisResult;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.llm.parser.LlmJsonResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterAnalysisService {
    private static final int CONTENT_LIMIT = 12000;

    private final LlmClientFactory clientFactory;
    private final LlmJsonResponseParser responseParser;

    public ChapterAnalysisResult analyze(Chapter chapter) {
        String prompt = """
                你是小说文本分析助手。请分析下面章节，输出严格JSON，不要输出解释。
                JSON格式：
                {
                  "summary": "200字以内章节摘要",
                  "mainCharacters": ["主要出场人物1", "主要出场人物2"],
                  "sceneBoundaries": [发生场景切换的段落编号，编号从1开始]
                }
                要求：
                1. sceneBoundaries 只填写新场景开始的段落编号，不要包含1。
                2. mainCharacters 只保留明确出场的人物名，去重。
                3. 无法判断时使用空数组。

                章节标题：%s
                章节内容：
                %s
                """.formatted(chapter.getTitle(), abbreviate(chapter.getContent()));
        String raw = clientFactory.taskClient("chapter-analysis")
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
}
