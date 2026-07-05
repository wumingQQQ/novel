package com.wuming.novel.service.impl;

import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.NovelPassage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NovelPassageSplitterTest {

    private final NovelPassageSplitter splitter = new NovelPassageSplitter();

    @Test
    void shouldSplitBySceneBoundariesWhenChapterAnalysisDone() {
        Chapter chapter = chapter("""
                第一段
                第二段
                第三段
                第四段
                第五段
                """);
        chapter.setSceneBoundaries("[3,5]");
        chapter.setAnalysisStatus("DONE");

        List<NovelPassage> passages = splitter.split(chapter, 10);

        assertThat(passages).hasSize(3);
        assertThat(passages).extracting(NovelPassage::getStartParagraph)
                .containsExactly(1, 3, 5);
        assertThat(passages).extracting(NovelPassage::getEndParagraph)
                .containsExactly(2, 4, 5);
        assertThat(passages).extracting(NovelPassage::getChapterSequence)
                .containsExactly(1, 2, 3);
        assertThat(passages).extracting(NovelPassage::getSequence)
                .containsExactly(10, 11, 12);
        assertThat(passages.get(0).getContent()).isEqualTo("第一段\n第二段");
        assertThat(passages.get(1).getContent()).isEqualTo("第三段\n第四段");
        assertThat(passages.get(2).getContent()).isEqualTo("第五段");
    }

    @Test
    void shouldFallbackToSlidingWindowWhenChapterAnalysisFailed() {
        Chapter chapter = chapter(paragraphs(20));
        chapter.setAnalysisStatus("FAILED");

        List<NovelPassage> passages = splitter.split(chapter, 1);

        assertThat(passages).hasSize(2);
        assertThat(passages).extracting(NovelPassage::getStartParagraph)
                .containsExactly(1, 13);
        assertThat(passages).extracting(NovelPassage::getEndParagraph)
                .containsExactly(15, 20);
    }

    private Chapter chapter(String content) {
        Chapter chapter = new Chapter();
        chapter.setId(100L);
        chapter.setNovelId(200L);
        chapter.setContent(content);
        return chapter;
    }

    private String paragraphs(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                builder.append('\n');
            }
            builder.append("第").append(i).append("段");
        }
        return builder.toString();
    }
}
