package com.wuming.novel.llm.checker;

import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.llmresponse.SceneSplitResponse;
import com.wuming.novel.domain.llmresponse.SceneSplitResponseWrapper;
import com.wuming.novel.exception.LLMResponseEmptyException;
import com.wuming.novel.text.TextAnchorMatcher;
import com.wuming.novel.text.TextMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SceneSplitResponseChecker {
    private final TextAnchorMatcher textAnchorMatcher;

    public List<SceneSplitResponse> check(Chapter chapter, String chapterContent, SceneSplitResponseWrapper responseWrapper) {
        if (responseWrapper == null || responseWrapper.scenes() == null || responseWrapper.scenes().isEmpty()) {
            throw new LLMResponseEmptyException("小说" + chapter.getNovelId() + "章节" + chapter.getId() + "分场景时llm响应为空");
        }

        List<SceneSplitResponse> responses = responseWrapper.scenes();
        for (int i = 0; i < responses.size(); i++) {
            checkSceneResponse(chapter, responses.get(i), i);
        }

        int previousStartIndex = -1;
        for (int i = 0; i < responses.size(); i++) {
            SceneSplitResponse current = responses.get(i);

            TextMatch startMatch = textAnchorMatcher.find(chapterContent, current.anchor()).orElse(null);
            TextMatch endMatch = i < responses.size() - 1
                    ? textAnchorMatcher.find(chapterContent, responses.get(i + 1).anchor()).orElse(null)
                    : null;
            int startIndex = startMatch == null ? -1 : startMatch.startIndex();
            int endIndex = i < responses.size() - 1
                    ? endMatch == null ? -1 : endMatch.startIndex()
                    : chapterContent.length();
            if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex || startIndex <= previousStartIndex) {
                String nextAnchor = i < responses.size() - 1 ? responses.get(i + 1).anchor() : null;
                throw new LlmResponseCheckException("小说" + chapter.getNovelId()
                        + "章节" + chapter.getSequence()
                        + "锚点匹配失败，chapterId: " + chapter.getId()
                        + ", sceneSequence: " + current.sequence()
                        + ", currentAnchorMatched: " + (startMatch != null)
                        + ", nextAnchorMatched: " + (i == responses.size() - 1 || endMatch != null)
                        + ", anchor: " + current.anchor()
                        + ", nextAnchor: " + nextAnchor);
            }
            previousStartIndex = startIndex;
        }
        return responses;
    }

    private void checkSceneResponse(Chapter chapter, SceneSplitResponse response, int index) {
        if (response == null) {
            throw new LlmResponseCheckException("小说" + chapter.getNovelId()
                    + "章节" + chapter.getSequence()
                    + "场景切分结果为空，chapterId: " + chapter.getId()
                    + ", sceneIndex: " + (index + 1));
        }
        if (response.sequence() != index + 1) {
            throw new LlmResponseCheckException("小说" + chapter.getNovelId()
                    + "章节" + chapter.getSequence()
                    + "场景序号不连续，chapterId: " + chapter.getId()
                    + ", expectedSequence: " + (index + 1)
                    + ", actualSequence: " + response.sequence());
        }
        if (response.anchor() == null || response.anchor().isBlank()) {
            throw new LlmResponseCheckException("小说" + chapter.getNovelId()
                    + "章节" + chapter.getSequence()
                    + "场景锚点为空，chapterId: " + chapter.getId()
                    + ", sceneSequence: " + response.sequence());
        }
    }
}
