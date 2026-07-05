package com.wuming.novel.service.impl;

import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.dto.PassageCharacterResult;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.llm.parser.LlmJsonResponseParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PassageCharacterRecognitionService {
    private final LlmClientFactory clientFactory;
    private final LlmJsonResponseParser responseParser;

    public PassageCharacterResult recognize(NovelPassage passage) {
        String prompt = """
                你是小说人物识别助手。请识别下面片段中明确出场或被直接对话提及的人物。
                输出严格JSON，不要输出解释。
                JSON格式：
                {
                  "characters": ["人物名1", "人物名2"]
                }
                要求：
                1. 只返回人物名称，不要返回身份描述。
                2. 去重。
                3. 无明确人物时返回空数组。

                小说片段：
                %s
                """.formatted(passage.getContent());
        String raw = clientFactory.taskClient("passage-character-recognition")
                .prompt()
                .user(prompt)
                .call()
                .content();
        return responseParser.parse(raw, PassageCharacterResult.class);
    }
}
