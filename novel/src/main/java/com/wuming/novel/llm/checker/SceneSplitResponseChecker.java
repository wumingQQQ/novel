package com.wuming.novel.llm.checker;

import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.llmresponse.SceneSplitResponseWrapper;
import com.wuming.novel.exception.LLMResponseEmptyException;
import com.wuming.novel.infrastructure.text.TextAnchorMatcher;
import com.wuming.novel.infrastructure.text.TextMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SceneSplitResponseChecker {
    private final TextAnchorMatcher textAnchorMatcher;

    public List<String> check(Chapter chapter, String chapterContent, SceneSplitResponseWrapper responseWrapper) {
        if (responseWrapper == null || responseWrapper.anchors() == null) {
            throw new LLMResponseEmptyException("小说" + chapter.getNovelId() + "章节" + chapter.getId() + "分场景时llm响应为空");
        }

        List<String> anchors = responseWrapper.anchors();
        int previousStartIndex = -1;
        for (int i = 0; i < anchors.size(); i++) {
            String anchor = anchors.get(i);
            checkAnchor(chapter, anchor, i);

            TextMatch startMatch = textAnchorMatcher.find(chapterContent, anchor).orElse(null);
            int startIndex = startMatch == null ? -1 : startMatch.startIndex();
            if (startIndex == -1 || startIndex <= previousStartIndex) {
                throw new LlmResponseCheckException("小说" + chapter.getNovelId()
                        + "章节" + chapter.getSequence()
                        + "锚点匹配失败，chapterId: " + chapter.getId()
                        + ", anchorIndex: " + i
                        + ", anchorMatched: " + (startMatch != null)
                        + ", anchor: " + anchor);
            }
            previousStartIndex = startIndex;
        }
        return anchors;
    }

    private void checkAnchor(Chapter chapter, String anchor, int index) {
        if (anchor == null || anchor.isBlank()) {
            throw new LlmResponseCheckException("小说" + chapter.getNovelId()
                    + "章节" + chapter.getSequence()
                    + "场景锚点为空，chapterId: " + chapter.getId()
                    + ", anchorIndex: " + index);
        }
    }
}
