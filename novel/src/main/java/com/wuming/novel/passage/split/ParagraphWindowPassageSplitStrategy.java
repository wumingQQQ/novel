package com.wuming.novel.passage.split;

import com.wuming.novel.config.PassageSplitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按段落滑动窗口切分Passage。
 */
@Component
@RequiredArgsConstructor
public class ParagraphWindowPassageSplitStrategy implements PassageSplitStrategy {
    private final PassageSplitProperties properties;

    @Override
    public PassageSplitStrategyType type() {
        return PassageSplitStrategyType.PARAGRAPH_WINDOW;
    }

    @Override
    public List<PassageSlice> split(String content) {
        List<String> paragraphs = paragraphs(content);
        if (paragraphs.isEmpty()) {
            return List.of();
        }

        PassageSplitProperties.Paragraph paragraphConfig = properties.getParagraph();
        int windowSize = paragraphConfig.getWindowSize();
        int overlapSize = paragraphConfig.getOverlapSize();
        validate(windowSize, overlapSize);

        List<PassageSlice> slices = new ArrayList<>();
        int step = windowSize - overlapSize;
        for (int start = 1; start <= paragraphs.size(); start += step) {
            int end = Math.min(start + windowSize - 1, paragraphs.size());
            String sliceContent = String.join("\n", paragraphs.subList(start - 1, end));
            slices.add(new PassageSlice(slices.size() + 1, start, end, sliceContent));
            if (end == paragraphs.size()) {
                break;
            }
        }
        return slices;
    }

    /**
     * 将章节正文按非空行切成段落，段落内容会去掉首尾空白。
     */
    private List<String> paragraphs(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String paragraph = line.trim();
            if (!paragraph.isEmpty()) {
                paragraphs.add(paragraph);
            }
        }
        return paragraphs;
    }

    /**
     * 提前校验配置，避免错误配置造成死循环或空窗口。
     */
    private void validate(int windowSize, int overlapSize) {
        if (windowSize <= 0) {
            throw new IllegalStateException("novel.passage.paragraph.window-size 必须大于0");
        }
        if (overlapSize < 0 || overlapSize >= windowSize) {
            throw new IllegalStateException("novel.passage.paragraph.overlap-size 必须大于等于0且小于window-size");
        }
    }
}
